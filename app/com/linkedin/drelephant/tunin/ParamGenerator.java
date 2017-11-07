package com.linkedin.drelephant.tunin;

import com.avaje.ebean.Expr;
import models.*;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

import java.util.ArrayList;
import java.util.List;

public abstract class ParamGenerator {


    abstract TunerState generateParamSet(TunerState jobTunerState);

    private List<Job> fetchJobsForParamSuggestion(){
        List<Job> jobsForSwarmSuggestion = new ArrayList<>();
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

    }

    private List<TunerState> getJobsTunerState(List<Job> tuninJobs){
        // Todo: fitness of current pop will be populated where?
        List<TunerState> tunerStateList = new ArrayList<>();
        for (Job job: tuninJobs){
            TunerState tunerState = new TunerState();
            tunerState.setTuningJob(job);
            JobSavedState jobSavedState = JobSavedState.find.byId(job.jobId);
            String savedState = new String(jobSavedState.savedState);
            // Need to add fitness to the current population
            tunerState.setStringTunerState(savedState);
            int algoId = job.algo.algoId;

            List<AlgoParam> algoParamList = AlgoParam.find.where().Expr.eq()

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

        List<JobExecution> jobExecutionList = new ArrayList<>();
        List<JobSuggestedParamValue> jobSuggestedParamValueList= new ArrayList<>();

        for (TunerState jobTunerState: jobTunerStateList){

            Job job = jobTunerState.getTuningJob();
            List<AlgoParam> paramList = jobTunerState.getParametersToTune();

            String stringTunerState = jobTunerState.getStringTunerState();
            JsonNode jsonTunerState = Json.toJson(stringTunerState);

            // Todo: From the json extract the currentPopulaion list of individuals
            List<Particle> suggestedPopulation = new ArrayList<>();
            JsonNode jsonSuggestedtPopulation = jsonTunerState.get("current_population");
            for (JsonNode jsonSuggestedParticle: jsonSuggestedtPopulation ){
                Particle suggestedParticle = new Particle();

                // Todo: fill the individual
                suggestedParticle.setCandidate();

                float fitness = Float.valueOf(jsonSuggestedParticle.get("fitness").textValue());
                suggestedParticle.setFitness(fitness);
            }

//            List<Particle> suggestedPopulation = jobTunerState.getCurrentPopulation();

            for (Particle suggestedParticle: suggestedPopulation){

                List<Float> candidate = suggestedParticle.getCandidate();
                JobExecution jobExecution = new JobExecution();
                JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();

                jobExecution.job = job;
                jobExecution.algo = job.algo;
                jobExecution.isDefaultExecution = false;

                for (int i=0; i< candidate.size() && i<paramList.size(); i++){
                    jobSuggestedParamValue.algoParam.paramId = paramList.get(i).paramId;
                    jobSuggestedParamValue.paramValue = Float.toString(candidate.get(i));
                }

                if (isParamConstraintViolated(jobSuggestedParamValue)){
                    jobExecution.paramSetState = JobExecution.ParamSetStatus.FITNESS_COMPUTED;
                    jobExecution.resourceUsage = (double) -1;
                    jobExecution.executionTime = (double) -1;
                    jobExecution.costMetric = 3 * job.averageResourceUsage * job.allowedMaxResourceUsagePercent;
                }
                else{
                    jobExecution.paramSetState = JobExecution.ParamSetStatus.CREATED;
                }

                jobSuggestedParamValue.jobExecution = jobExecution;
                suggestedParticle.setJobExecution(jobExecution);

                jobExecutionList.add(jobExecution);
                jobSuggestedParamValueList.add(jobSuggestedParamValue);
            }
        }

        saveSuggestedParamMetadata(jobExecutionList);
        saveSuggestedParams(jobSuggestedParamValueList);
        saveTunerState(jobTunerStateList);
    }

    private boolean isParamConstraintViolated(JobSuggestedParamValue jobSuggestedParamValue){


    }

    private void saveTunerState(List<TunerState> jobTunerStateList){

    }

    private void saveSuggestedParams(List<JobSuggestedParamValue> jobSuggestedParamValueList){

    }

    private void saveSuggestedParamMetadata(List<JobExecution> jobExecutionList){

    }


    public void ParamGenerator(){
        List<Job> jobsForSwarmSuggestion = fetchJobsForParamSuggestion();
        List<TunerState> jobTunerStateList= getJobsTunerState(jobsForSwarmSuggestion);
        List<TunerState> updatedJobTunerStateList = new ArrayList<>();
        for (TunerState jobTunerState: jobTunerStateList){
            TunerState newJobTunerState = generateParamSet(jobTunerState);
            updatedJobTunerStateList.add(newJobTunerState);
        }
        updateDatabase(updatedJobTunerStateList);

    }

}
