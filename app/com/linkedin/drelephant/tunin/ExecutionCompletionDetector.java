package com.linkedin.drelephant.tunin;

import models.SuggestedParamSetMetaData;

import java.util.ArrayList;
import java.util.List;

public abstract class ExecutionCompletionDetector {


    abstract boolean isExecutionComplete(TuninJobExecution runningExecution);

    public List<TuninJobExecution> fetchCompletedExecutions(){
        List<TuninJobExecution> runningExecutions = fetchRunningJobExecutions();
        List<TuninJobExecution> completedJobExecutions = new ArrayList<>();
        for (TuninJobExecution runningExecution: runningExecutions){
            if (isExecutionComplete(runningExecution)){
                // TODO: Any changes in runningExecution object?
                completedJobExecutions.add(runningExecution);
            }
        }
        return completedJobExecutions;
    }


    public void updateDatabse(List<TuninJobExecution> completedJobExecutions){
      // TODO
    }

    public List<TuninJobExecution> fetchRunningJobExecutions(){
        List<TuninJobExecution> runningJobExecutions = new ArrayList<>();

        for(SuggestedParamSetMetaData paramSetMetaData: SuggestedParamSetMetaData.find.where().eq("paramSetState", ParamSetState.SENT).findList())
        {
            TuninJobExecution runningJobExecution = new TuninJobExecution();
            runningJobExecution._jobId = paramSetMetaData.jobId;
            runningJobExecution._schedulerJobExecutionId = paramSetMetaData.schedulerJobExecutionId;
            runningJobExecutions.add(runningJobExecution);
        }

        return runningJobExecutions;
    }
}
