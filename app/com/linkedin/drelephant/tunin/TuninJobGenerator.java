package com.linkedin.drelephant.tunin;

import java.util.List;

public class TuninJobGenerator {

    // return list of execution ids
    public List<TuninJobExecution> fetchCompletedTuninJobExecutions(){
        List<TuninJobExecution> tuninJobExecutions;
        /*
        1. fetch running job: From db query and return the jobs which are in running state.
        2. On the running jobs, check if they are completed.
        To figure out: For a azkaban job, how will you know if it is completed?
         */
        return tuninJobExecutions;
    }

    public List<TuninJob> fetchTuninJobsForSwarmGeneration(){
        /*
        1. Fetch the jobs with no unsent param set and with all have their fitness computed.
         */
    }

}
