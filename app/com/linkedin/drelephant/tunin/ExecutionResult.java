package com.linkedin.drelephant.tunin;

import java.util.List;
import models.AppResult;

public class ExecutionResult {

    public void fetchExecutionResult(List<TuninJobExecution> completedJobExecutions) {
        for(TuninJobExecution completedJobExecution: completedJobExecutions){
            // fetch execution result for each job execution and update completeJobExecution object
        }

        // call computeCostFunction (it will apply penalty if needed to the resource usage)

    }

    private void computeCostFunction (List<TuninJobExecution> completedJobExecutions){

        // compute cost function
        // call updateDB
    }

    private void updateDB(List<TuninJobExecution> completedJobExecutions){

    }
}
