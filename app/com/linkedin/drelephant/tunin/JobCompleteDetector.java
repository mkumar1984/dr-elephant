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

import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.TuningJobExecution;
import models.TuningJobExecution.ParamSetStatus;

import org.apache.log4j.Logger;

import controllers.AutoTuningMetricsController;
import play.libs.Json;


/**
 * This class pools the scheduler for completion status of execution and updates the database with current status
 * of the job.
 */
public abstract class JobCompleteDetector {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  /**
   * Updates the status of completed executions
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  public List<TuningJobExecution> updateCompletedExecutions() throws MalformedURLException, URISyntaxException {
    logger.info("Starting JobCompleteDetector");
    List<TuningJobExecution> runningExecutions = getStartedExecutions();
    List<TuningJobExecution> completedExecutions = getCompletedExecutions(runningExecutions);
    updateExecutionStatus(completedExecutions);
    updateMetrics(completedExecutions);
    logger.info("Finished JobCompleteDetector");
    return completedExecutions;
  }

  /**
   * This method is for updating metrics for auto tuning monitoring for job completion daemon
   * @param completedExecutions
   */
  private void updateMetrics(List<TuningJobExecution> completedExecutions) {
    for (TuningJobExecution tuningJobExecution : completedExecutions) {
      if (tuningJobExecution.paramSetState == ParamSetStatus.EXECUTED) {
        if (tuningJobExecution.jobExecution.executionState == ExecutionState.SUCCEEDED) {
          AutoTuningMetricsController.markSuccessfulJobs();
        } else if (tuningJobExecution.jobExecution.executionState == ExecutionState.FAILED) {
          AutoTuningMetricsController.markFailedJobs();
        }
      }
    }
  }

  /**
   * Returns the list of executions which have already received param suggestion
   * @return JobExecution list
   */
  private List<TuningJobExecution> getStartedExecutions() {
    logger.debug("fetching started executions");
    List<TuningJobExecution> tuningJobExecutionList = new ArrayList<TuningJobExecution>();
    try {
      tuningJobExecutionList =
          TuningJobExecution.find.select("*").where().eq(TuningJobExecution.TABLE.paramSetState, ParamSetStatus.SENT)
              .findList();
    } catch (NullPointerException e) {
      logger.error("Error in getStartedExecutions ", e);
    }
    logger.debug("started executions fetched");
    return tuningJobExecutionList;
  }

  /**
   * Returns the list of completed executions.
   * @param jobExecutions Started Execution list
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  protected abstract List<TuningJobExecution> getCompletedExecutions(List<TuningJobExecution> jobExecutions)
      throws MalformedURLException, URISyntaxException;

  /**
   * Updates the job execution status
   * @param jobExecutions JobExecution list
   * @return Update status
   */
  private void updateExecutionStatus(List<TuningJobExecution> jobExecutions) {
    for (TuningJobExecution tuningJobExecution : jobExecutions) {
      JobExecution jobExecution = tuningJobExecution.jobExecution;
      logger.info("Updating jobExecution: " + Json.toJson(jobExecution));
      jobExecution.update();
      tuningJobExecution.update();
    }
  }
}
