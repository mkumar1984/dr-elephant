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

package com.linkedin.drelephant.tuning;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.JobSuggestedParamSet;
import models.JobSuggestedParamSet.ParamSetStatus;

import org.apache.log4j.Logger;

import controllers.AutoTuningMetricsController;


/**
 * This class pools the scheduler for completion status of execution and updates the database with current status
 * of the job.
 */
public abstract class JobCompleteDetector {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  /**
   * Updates the status of completed executions
   * @return List of completed executions
   * @throws MalformedURLException MalformedURLException
   * @throws URISyntaxException URISyntaxException
   */
  public List<JobSuggestedParamSet> updateCompletedExecutions() throws MalformedURLException, URISyntaxException {
    logger.info("Checking execution status");
    List<JobSuggestedParamSet> runningExecutions = getStartedExecutions();
    List<JobSuggestedParamSet> completedExecutions = getCompletedExecutions(runningExecutions);
    updateExecutionStatus(completedExecutions);
    updateMetrics(completedExecutions);
    logger.info("Finished updating execution status");
    return completedExecutions;
  }

  /**
   * This method is for updating metrics for auto tuning monitoring for job completion daemon
   * @param completedExecutions List completed job executions
   */
  private void updateMetrics(List<JobSuggestedParamSet> completedExecutions) {
    for (JobSuggestedParamSet jobSuggestedParamSet : completedExecutions) {
      if (jobSuggestedParamSet.paramSetState.equals(ParamSetStatus.EXECUTED)) {
        if (jobSuggestedParamSet.jobExecution.executionState.equals(ExecutionState.SUCCEEDED)) {
          AutoTuningMetricsController.markSuccessfulJobs();
        } else if (jobSuggestedParamSet.jobExecution.executionState.equals(ExecutionState.FAILED)) {
          AutoTuningMetricsController.markFailedJobs();
        }
      }
    }
  }

  /**
   * Returns the list of executions which have already received param suggestion
   * @return JobExecution list
   */
  private List<JobSuggestedParamSet> getStartedExecutions() {
    logger.info("Fetching the executions which were running");
    List<JobSuggestedParamSet> jobSuggestedParamSetList = new ArrayList<JobSuggestedParamSet>();
    try {
      jobSuggestedParamSetList = JobSuggestedParamSet.find.select("*")
          .where()
          .eq(JobSuggestedParamSet.TABLE.paramSetState, ParamSetStatus.SENT)
          .findList();
    } catch (NullPointerException e) {
      logger.info("None of the executions were running ", e);
    }
    logger.info("Number of executions which were in running state: " + jobSuggestedParamSetList.size());
    return jobSuggestedParamSetList;
  }

  /**
   * Returns the list of completed executions.
   * @param jobExecutions Started Execution list
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  protected abstract List<JobSuggestedParamSet> getCompletedExecutions(List<JobSuggestedParamSet> jobExecutions)
      throws MalformedURLException, URISyntaxException;

  /**
   * Updates the job execution status
   * @param jobExecutions JobExecution list
   * @return Update status
   */
  private void updateExecutionStatus(List<JobSuggestedParamSet> jobExecutions) {
    logger.info("Updating status of executions completed since last iteration");
    for (JobSuggestedParamSet jobSuggestedParamSet : jobExecutions) {
      JobExecution jobExecution = jobSuggestedParamSet.jobExecution;
      logger.info("Updating execution status to EXECUTED for the execution: " + jobExecution.jobExecId);
      jobExecution.update();
      jobSuggestedParamSet.update();
    }
  }
}
