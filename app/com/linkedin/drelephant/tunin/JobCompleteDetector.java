/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.tunin;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.TuningJobExecution;
import models.TuningJobExecution.ParamSetStatus;
import org.apache.log4j.*;
import play.libs.Json;


/**
 * This class pools the scheduler for completion status of execution and updates the database
 */

// Todo: Rename class to ExecutionStatusFetcher
// Todo: This is not generic in terms of schedulers

public class JobCompleteDetector {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  // Todo: rename?
  private AzkabanJobStatusUtil _azkabanJobStatusUtil;

  // Todo: rename?
  public enum AzkabanJobStatus {
    FAILED,
    CANCELLED,
    KILLED,
    SUCCEEDED
  }

  /**
   * Updates the status of completed executions
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  public List<TuningJobExecution> updateCompletedExecutions() throws MalformedURLException, URISyntaxException {
    logger.debug ("Checking execution status");

    List<TuningJobExecution> runningExecutions = getStartedExecutions();
    // Todo: The following function will be different for different schedulers
    List<TuningJobExecution> completedExecutions = getCompletedExecutions(runningExecutions);
    updateExecutionStatus(completedExecutions);
    logger.info("Execution status updated");
    return completedExecutions;
  }

  /**
   * Returns the list of executions which have already received param suggestion
   * @return JobExecution list
   */
  public List<TuningJobExecution> getStartedExecutions() {
    logger.debug ("fetching started executions");

    List<TuningJobExecution> tuningJobExecutionList = new ArrayList<TuningJobExecution>();
    try {
      tuningJobExecutionList =
          TuningJobExecution.find.select ("*").fetch (TuningJobExecution.TABLE.jobExecution, "*").where ().
              eq (TuningJobExecution.TABLE.paramSetState, ParamSetStatus.SENT).findList ();
    } catch(NullPointerException e){
      logger.error("CompletionDetector: 0 started executions found");
    }
    logger.debug ("started executions fetched");
    return tuningJobExecutionList;
  }

  /**
   * Returns the list of completed executions
   * @param jobExecutions Started Execution list
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  public List<TuningJobExecution> getCompletedExecutions(List<TuningJobExecution> jobExecutions) throws MalformedURLException,
                                                                                     URISyntaxException {
    logger.debug("Fetching completed executions" + Json.toJson(jobExecutions));
    List<TuningJobExecution> completedExecutions = new ArrayList<TuningJobExecution>();
    try
    {
      for (TuningJobExecution tuningJobExecution : jobExecutions) {

        JobExecution jobExecution = tuningJobExecution.jobExecution;

        if (_azkabanJobStatusUtil == null) {
          logger.info("Initializing  AzkabanJobStatusUtil");
          _azkabanJobStatusUtil = new AzkabanJobStatusUtil(jobExecution.flowExecution.flowExecId);
        }

        try {
          Map<String, String> jobStatus = _azkabanJobStatusUtil.getJobsFromFlow(jobExecution.flowExecution.flowExecId);
          if (jobStatus != null) {
            for (Map.Entry<String, String> job : jobStatus.entrySet()) {
              logger.info("Job Found:" + job.getKey() + ". Status: " + job.getValue());
              if (job.getKey().equals(jobExecution.job.jobName)) {
                if (job.getValue().equals(AzkabanJobStatus.FAILED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.FAILED;
                }
                if (job.getValue().equals(AzkabanJobStatus.CANCELLED.toString()) || job.getValue().equals(AzkabanJobStatus.KILLED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.CANCELLED;
                }
                if (job.getValue().equals(AzkabanJobStatus.SUCCEEDED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.SUCCEDED;
                }
                if (tuningJobExecution.paramSetState.equals(ParamSetStatus.EXECUTED)) {
                  completedExecutions.add(tuningJobExecution);
                }
              }
            }
          } else {
            logger.info("No jobs found for flow execution: " + jobExecution.flowExecution.flowExecId);
          }
        } catch(Exception e){
          logger.error(e);
        }
      }
    } catch(Exception e) {
      logger.error(e);
    }
    logger.debug("Completed executions fetched");
    return completedExecutions;
  }

  /**
   * Updates the job execution status
   * @param jobExecutions JobExecution list
   * @return Update status
   */
  public boolean updateExecutionStatus(List<TuningJobExecution> jobExecutions) {
    // Todo: what is the use of this?
    boolean updateStatus = true;
    for (TuningJobExecution tuningJobExecution : jobExecutions) {

      JobExecution jobExecution = tuningJobExecution.jobExecution;
      logger.debug ("Updating jobExecution: " + jobExecution.jobExecId);
      jobExecution.update();
      tuningJobExecution.update();
    }
    return updateStatus;
  }
}
