package com.linkedin.drelephant.tunin;

import com.avaje.ebean.Expr;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import models.*;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import scala.None;

import java.util.ArrayList;
import java.util.List;

public abstract class ParamGenerator {


    abstract TunerState generateParamSet(TunerState jobTunerState);

    private List<Job> fetchJobsForParamSuggestion(){
        List<Job> jobsForSwarmSuggestion = new ArrayList<Job>();
        for (JobExecution paramSetMetaData: JobExecution.find.where()
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.SENT))
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.CREATED))
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.EXECUTED))
                .select("jobId").setDistinct(true).findSet()){
            Job tuninJob = new Job();
            tuninJob.jobId = paramSetMetaData.job.jobId;
            jobsForSwarmSuggestion.add(tuninJob);
        }
        return jobsForSwarmSuggestion;
    }

    private List<Particle> jsonToParticleList(JsonNode jsonParticleList){

        List<Particle> particleList = new ArrayList<Particle>();
        for (JsonNode jsonParticle: jsonParticleList ){
            Particle particle = new Particle();

            List<Float> candidate = new ArrayList<Float>();
            JsonNode jsonCandidate = jsonParticle.get("_candidate");
            for (JsonNode jsonValue: jsonCandidate){
                float value = jsonValue.floatValue();
                candidate.add(value);
            }
            boolean maximize =jsonParticle.get("maximize").asBoolean();
            double birthDate = jsonParticle.get("birthdate").asDouble();
            float fitness = jsonParticle.get("fitness").floatValue();

            particle.setCandidate(candidate);
            particle.setMaximize(maximize);
            particle.setBirthDate(birthDate);
            particle.setFitness(fitness);
            particleList.add(particle);
        }
        return particleList;
    }

    private JsonNode particleListToJson(List<Particle> particleList){
        JsonNode jsonNode = Json.toJson(particleList);
        return jsonNode;
    }

    private List<TunerState> getJobsTunerState(List<Job> tuninJobs){
        List<TunerState> tunerStateList = new ArrayList<TunerState>();
        for (Job job: tuninJobs){
            TunerState tunerState = new TunerState();
            tunerState.setTuningJob(job);
            JobSavedState jobSavedState = JobSavedState.find.byId(job.jobId);
            String savedState = new String(jobSavedState.savedState);
            // Todo: Need to add fitness to the current population
            tunerState.setStringTunerState(savedState);
            int algoId = job.algo.algoId;
            List<AlgoParam> algoParamList = AlgoParam.find.where().eq("algoId", algoId).findList();
            tunerState.setParametersToTune(algoParamList);
            tunerStateList.add(tunerState);
        }
        return tunerStateList;
    }




    private void updateDatabase(List<TunerState> jobTunerStateList){
        /**
         * corresponding ot every tuner state a list of jobsuggestedparam value
         * check for penalty
         * Update the param in the job execution table, hob suggestedparam table
         * update the tuner state
         */

        for (TunerState jobTunerState: jobTunerStateList){

            Job job = jobTunerState.getTuningJob();
            List<AlgoParam> paramList = jobTunerState.getParametersToTune();

            String stringTunerState = jobTunerState.getStringTunerState();
            JsonNode jsonTunerState = Json.toJson(stringTunerState);

            JsonNode jsonSuggestedPopulation = jsonTunerState.get("current_population");

            List<Particle> suggestedPopulation = jsonToParticleList(jsonSuggestedPopulation);

            for (Particle suggestedParticle: suggestedPopulation){
                List<JobSuggestedParamValue> jobSuggestedParamValueList= new ArrayList<JobSuggestedParamValue>();
                List<Float> candidate = suggestedParticle.getCandidate();
                JobExecution jobExecution = new JobExecution();

                jobExecution.job = job;
                jobExecution.algo = job.algo;
                jobExecution.isDefaultExecution = false;

                for (int i=0; i< candidate.size() && i<paramList.size(); i++){
                    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();
                    int paramId = paramList.get(i).paramId;
                    AlgoParam algoParam = AlgoParam.find.byId(paramId);
                    jobSuggestedParamValue.algoParam = algoParam;
                    jobSuggestedParamValue.paramValue = Float.toString(candidate.get(i));
                    jobSuggestedParamValueList.add(jobSuggestedParamValue);
                }

                if (isParamConstraintViolated(jobSuggestedParamValueList)){
                    jobExecution.paramSetState = JobExecution.ParamSetStatus.FITNESS_COMPUTED;
                    jobExecution.resourceUsage = (double) -1;
                    jobExecution.executionTime = (double) -1;
                    jobExecution.costMetric = 3 * job.averageResourceUsage * job.allowedMaxResourceUsagePercent;
                }
                else{
                    jobExecution.paramSetState = JobExecution.ParamSetStatus.CREATED;
                }

                Long paramSetId = saveSuggestedParamMetadata(jobExecution);
                for (JobSuggestedParamValue jobSuggestedParamValue: jobSuggestedParamValueList){
                    jobSuggestedParamValue.jobExecution = jobExecution;
                    //jobSuggestedParamValue.jobExecution.paramSetId = paramSetId;
                }
                suggestedParticle.setPramSetId(paramSetId);
                saveSuggestedParams(jobSuggestedParamValueList);
            }

            JsonNode updatedJsonSuggestedPopulation = particleListToJson(suggestedPopulation);
            ObjectNode updatedJsonTunerState = (ObjectNode) jsonTunerState;
            updatedJsonTunerState.put("current_population", updatedJsonSuggestedPopulation);
            String updatedStringTunerState = Json.stringify(updatedJsonTunerState);
            jobTunerState.setStringTunerState(updatedStringTunerState);
        }
        saveTunerState(jobTunerStateList);
    }

    private boolean isParamConstraintViolated(List<JobSuggestedParamValue> jobSuggestedParamValueList){
        //[1] sort.mb > 60% of map.memory: To avoid heap memory failure
        //[2] map.memory - sort.mb < 768: To avoid heap memory failure
        //[3] pig.maxCombinedSplitSize > 1.8*mapreduce.map.memory.mb

        int violations = 0;
        int mrSortMemory = -1;
        int mrMapMemory = -1;
        int pigMaxCombinedSplitSize = -1;

        for (JobSuggestedParamValue jobSuggestedParamValue: jobSuggestedParamValueList){
            if(jobSuggestedParamValue.algoParam.paramName.equals("mapreduce.task.io.sort.mb")){
                mrSortMemory = Integer.parseInt(jobSuggestedParamValue.paramValue);
            } else if(jobSuggestedParamValue.algoParam.paramName.equals("mapreduce.map.memory.mb")){
                mrMapMemory = Integer.parseInt(jobSuggestedParamValue.paramValue);
            }else if(jobSuggestedParamValue.algoParam.paramName.equals("pig.maxCombinedSplitSize")){
                pigMaxCombinedSplitSize = Integer.parseInt(jobSuggestedParamValue.paramValue);
            }
        }

        if (mrSortMemory!=-1 && mrMapMemory!=-1){
            if (mrSortMemory>0.6*mrMapMemory){
                violations++;
            }
            if (mrMapMemory-mrSortMemory < 768){
                violations++;
            }
        }

        if(pigMaxCombinedSplitSize!=-1 && mrMapMemory!=-1 && (pigMaxCombinedSplitSize > 1.8*mrMapMemory)){
            violations++;
        }

        if(violations==0){
            return false;
        }
        else{
            return true;
        }
    }

    private void saveTunerState(List<TunerState> tunerStateList){
        for (TunerState tunerState: tunerStateList){
            JobSavedState jobSavedState = new JobSavedState();
            jobSavedState.jobId = tunerState.getTuningJob().jobId;
            jobSavedState.savedState = tunerState.getStringTunerState().getBytes();
            jobSavedState.save();
        }
    }

    private void saveSuggestedParams(List<JobSuggestedParamValue> jobSuggestedParamValueList){
        for(JobSuggestedParamValue jobSuggestedParamValue: jobSuggestedParamValueList){
            jobSuggestedParamValue.save();
        }
    }

    private Long saveSuggestedParamMetadata(JobExecution jobExecution){
        // TODO: CHECK
        jobExecution.save();
        return jobExecution.paramSetId;
    }


    public void ParamGenerator(){
        List<Job> jobsForSwarmSuggestion = fetchJobsForParamSuggestion();
        List<TunerState> jobTunerStateList= getJobsTunerState(jobsForSwarmSuggestion);
        List<TunerState> updatedJobTunerStateList = new ArrayList<TunerState>();
        for (TunerState jobTunerState: jobTunerStateList){
            TunerState newJobTunerState = generateParamSet(jobTunerState);
            updatedJobTunerStateList.add(newJobTunerState);
        }
        updateDatabase(updatedJobTunerStateList);

    }

}
