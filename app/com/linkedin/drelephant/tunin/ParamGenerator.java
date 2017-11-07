package com.linkedin.drelephant.tunin;

import com.avaje.ebean.Expr;
import models.*;

import java.util.ArrayList;
import java.util.List;

public abstract class ParamGenerator {

    private List<Job> fetchJobsForParamSuggestion(){
        List<Job> jobsForSwarmSuggestion = new ArrayList<>();
        for (JobExecution paramSetMetaData: JobExecution.find.where()
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.SENT))
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.CREATED))
                .not(Expr.eq("paramSetState", JobExecution.ParamSetStatus.EXECUTED))
                .select("jobId").setDistinct(true).findSet()){
            Job tuninJob = new Job();
            tuninJob.jobId = paramSetMetaData.jobId;
            jobsForSwarmSuggestion.add(tuninJob);
        }
        return jobsForSwarmSuggestion;
    }

    private List<TunerState> getJobsTunerState(List<Job> tuninJobs){
        // can be done only after saved state model is created
        // Todo: fitness of current pop will be populated where?
    }

    abstract TunerState generateParamSet(TunerState jobTunerState);


    private void updateDatabase(List<TunerState> jobTunerStateList){
        /**
         * corresponding ot every tuner state a list of jobsuggestedparam value
         * check for penalty
         * Update the param in the job execution table, hob suggestedparam table
         * update the tuner state
         */
        saveTunerState(jobTunerStateList);

        for (TunerState jobTunerState: jobTunerStateList){

            Job job = jobTunerState.getTuningJob();
            int jobId = job.jobId;
            List<AlgoParam> paramList = jobTunerState.getParametersToTune();
            List<Individual> suggestedPopulation = jobTunerState.getCurrentPopulation();

            List<JobSuggestedParamValue> jobSuggestedParamValueList= new ArrayList<>();

            for (Individual suggestedParticle: suggestedPopulation){
                List<Float> candidate = suggestedParticle.getCandidate();
                JobExecution jobExecution = new JobExecution();
                JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();

                jobExecution.jobId = jobId;
                jobExecution.algoId = job.algo.algoId;
                jobExecution.isDefaultExecution = false;
                for (int i=0; i< candidate.size() && i<paramList.size(); i++){
                    jobSuggestedParamValue.paramId = paramList.get(i).paramId;
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

                Long paramSetId = saveSuggestedParamMetadata(jobExecution);
                jobSuggestedParamValue.paramSetId = paramSetId;
                jobSuggestedParamValueList.add(jobSuggestedParamValue);
            }
            saveSuggestedParams(jobSuggestedParamValueList);
        }
    }

    private boolean isParamConstraintViolated(JobSuggestedParamValue jobSuggestedParamValue){


    }

    private void saveTunerState(List<TunerState> jobTunerStateList){

    }

    private void saveSuggestedParams(List<JobSuggestedParamValue> jobSuggestedParamValueList){

    }

    private Long saveSuggestedParamMetadata(JobExecution jobExecutionList){

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
