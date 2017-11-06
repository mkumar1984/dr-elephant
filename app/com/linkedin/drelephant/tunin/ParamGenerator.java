package com.linkedin.drelephant.tunin;

import com.avaje.ebean.Expr;
import models.SuggestedParamSetMetaData;

import java.util.ArrayList;
import java.util.List;

public abstract class ParamGenerator {

    private List<TuninJob> fetchJobsForParamSuggestion(){
        List<TuninJob> jobsForSwarmSuggestion = new ArrayList<>();

        for (SuggestedParamSetMetaData paramSetMetaData: SuggestedParamSetMetaData.find.where().not(Expr.eq("paramSetState", ParamSetState.SENT)).not(Expr.eq("paramSetState", ParamSetState.CREATED)).not(Expr.eq("paramSetState", ParamSetState.EXECUTED)).select("jobId").setDistinct(true).findSet()){
            TuninJob tuninJob = new TuninJob();
            tuninJob._jobId = paramSetMetaData.jobId;
            jobsForSwarmSuggestion.add(tuninJob);
        }
        return jobsForSwarmSuggestion;
    }

    private List<TunerState> getJobsTunerState(List<TuninJob> tuninJobs){

    }

    abstract TunerState generateParamSet(TunerState jobTunerState);


    private void saveTunerState(List<TunerState> jobTunerStateList){

    }

    private boolean isParamConstraintViolated(){

    }

    public void ParamGenerator(){
        List<TuninJob> jobsForSwarmSuggestion = fetchJobsForParamSuggestion();
        List<TunerState> jobTunerStateList= getJobsTunerState(jobsForSwarmSuggestion);
        List<TunerState> updatedJobTunerStateList = new ArrayList<>();
        for (TunerState jobTunerState: jobTunerStateList){
            TunerState newJobTunerState = generateParamSet(jobTunerState);
            updatedJobTunerStateList.add(newJobTunerState);
        }
        saveTunerState(updatedJobTunerStateList);

    }

}
