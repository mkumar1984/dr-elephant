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
import models.TuningJobExecution;
import models.TuningJobExecution.ParamSetStatus;

import org.apache.log4j.Logger;

import play.libs.Json;


/**
 * This class pools the scheduler for completion status of execution and updates the database
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
    logger.info("Finished JobCompleteDetector");
    return completedExecutions;
  }

  /**
   * Returns the list of executions which have already received param suggestion
   * @return JobExecution list
   */
  public List<TuningJobExecution> getStartedExecutions() {
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
   * Returns the list of completed executions
   * @param jobExecutions Started Execution list
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  public abstract List<TuningJobExecution> getCompletedExecutions(List<TuningJobExecution> jobExecutions)
      throws MalformedURLException, URISyntaxException;

  /**
   * Updates the job execution status
   * @param jobExecutions JobExecution list
   * @return Update status
   */
  public boolean updateExecutionStatus(List<TuningJobExecution> jobExecutions) {
    boolean updateStatus = true;
    for (TuningJobExecution tuningJobExecution : jobExecutions) {
      JobExecution jobExecution = tuningJobExecution.jobExecution;
      logger.debug("Updating jobExecution: " + Json.toJson(jobExecution));
      jobExecution.update();
      tuningJobExecution.update();
    }
    return updateStatus;
  }
}
