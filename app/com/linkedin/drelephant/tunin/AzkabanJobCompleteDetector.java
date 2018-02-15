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

import com.linkedin.drelephant.clients.azkaban.AzkabanJobStatusUtil;

import play.libs.Json;


/**
 * Job completion detector for azkaban jobs. This utility uses azkaban rest api to find out if the jobs in a flow are
 * completed or not.
 */
public class AzkabanJobCompleteDetector extends JobCompleteDetector {

  private static final Logger logger = Logger.getLogger(AzkabanJobCompleteDetector.class);
  private AzkabanJobStatusUtil _azkabanJobStatusUtil;

  public enum AzkabanJobStatus {
    FAILED, CANCELLED, KILLED, SUCCEEDED
  }

  /**
   * Returns the list of completed executions
   * @param jobExecutions Started Execution list
   * @return List of completed executions
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  protected List<TuningJobExecution> getCompletedExecutions(List<TuningJobExecution> jobExecutions)
      throws MalformedURLException, URISyntaxException {
    logger.debug("Fetching completed executions" + Json.toJson(jobExecutions));
    List<TuningJobExecution> completedExecutions = new ArrayList<TuningJobExecution>();
    try {
      for (TuningJobExecution tuningJobExecution : jobExecutions) {

        JobExecution jobExecution = tuningJobExecution.jobExecution;

        logger.info("Checking completion for job execution: " + Json.toJson(tuningJobExecution));

        if (_azkabanJobStatusUtil == null) {
          logger.info("Initializing  AzkabanJobStatusUtil");
          _azkabanJobStatusUtil = new AzkabanJobStatusUtil(jobExecution.flowExecution.flowExecId);
        }

        try {
          Thread.sleep(2000);
          Map<String, String> jobStatus = _azkabanJobStatusUtil.getJobsFromFlow(jobExecution.flowExecution.flowExecId);
          if (jobStatus != null) {
            for (Map.Entry<String, String> job : jobStatus.entrySet()) {
              logger.info("Job Found:" + job.getKey() + ". Status: " + job.getValue());
              if (job.getKey().equals(jobExecution.job.jobName)) {
                if (job.getValue().equals(AzkabanJobStatus.FAILED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.FAILED;
                }
                if (job.getValue().equals(AzkabanJobStatus.CANCELLED.toString()) || job.getValue()
                    .equals(AzkabanJobStatus.KILLED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.CANCELLED;
                }
                if (job.getValue().equals(AzkabanJobStatus.SUCCEEDED.toString())) {
                  tuningJobExecution.paramSetState = ParamSetStatus.EXECUTED;
                  jobExecution.executionState = ExecutionState.SUCCEEDED;
                }
                if (tuningJobExecution.paramSetState.equals(ParamSetStatus.EXECUTED)) {
                  completedExecutions.add(tuningJobExecution);
                }
              }
            }
          } else {
            logger.debug("No jobs found for flow execution: " + jobExecution.flowExecution.flowExecId);
          }
        } catch (Exception e) {
          logger.error("Error get status for execution with id: " + jobExecution.id, e);
        }
      }
    } catch (Exception e) {
      logger.error("Error in getCompletedExecutions ", e);
      e.printStackTrace();
    }
    logger.debug("Completed executions fetched");
    return completedExecutions;
  }
}
