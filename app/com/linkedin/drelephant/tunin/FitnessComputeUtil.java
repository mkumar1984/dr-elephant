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

import java.util.ArrayList;
import java.util.List;
import models.*;
import models.TuningJobExecution.ParamSetStatus;

import org.apache.log4j.Logger;
import play.libs.Json;

// Todo: rename class to FitnessComputer
// Todo: Exception handling. Difficulty in understanding logic

/**
 * This class computes the fitness of the suggested parameters after the execution is complete
 */
public class FitnessComputeUtil {
  private static final Logger logger = Logger.getLogger(FitnessComputeUtil.class);

  /**
   * Updates the metrics (execution time, resource usage, cost function) of the completed executions whose metrics are
   * not computed.
   * @return List of job execution
   */
  public List<TuningJobExecution> updateFitness() {
    logger.info("Updating fitness");
    List<TuningJobExecution> completedExecutions= getCompletedExecutions();
    updateExecutionMetrics(completedExecutions);
    logger.info("Fitness updated");
    return completedExecutions;

  }

  /**
   * Returns the list of completed executions whose metrics are not computed
   * @return List of job execution
   */
  public List<TuningJobExecution> getCompletedExecutions() {
    logger.debug("Inside getCompletedExecutions jobs");
    List<TuningJobExecution> jobExecutions = new ArrayList<TuningJobExecution> ();

    try{
      jobExecutions =
          TuningJobExecution.find.select("*")
              //.fetch(TuningJobExecution.TABLE.jobExecution, "*")
              //.fetch(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job, "*")   //todo: required?
              //.fetch(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.flowExecution, "*") //todo: required?
              .where().eq(TuningJobExecution.TABLE.paramSetState, ParamSetStatus.EXECUTED).findList();
    } catch(NullPointerException e){
      logger.info("No completed executions found for computing fitness" , e);
    }
    logger.debug("Finished getCompletedExecutions jobs");
    return jobExecutions;
  }

  /**
   * Updates the execution metrics
   * @param completedExecutions List of completed executions
   */
  public void updateExecutionMetrics(List<TuningJobExecution> completedExecutions) {
    logger.debug("Updating execution metrics");
    for (TuningJobExecution tuningJobExecution : completedExecutions) {

      logger.info("Completed executions before updating metric: " + Json.toJson (tuningJobExecution));

      try {
        JobExecution jobExecution = tuningJobExecution.jobExecution;
        logger.info ("Job Execution Update: Flow Execution ID " + jobExecution.flowExecution.flowExecId + " Job ID "
            + jobExecution.jobExecId);
        List<AppResult> results = AppResult.find.select ("*")
            .fetch (AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
            .fetch (AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
                "*")
            .where ()
            .eq (AppResult.TABLE.FLOW_EXEC_ID, jobExecution.flowExecution.flowExecId)
            .eq (AppResult.TABLE.JOB_EXEC_ID, jobExecution.jobExecId)
            .findList ();
        if (results != null && results.size () > 0) {
          Long totalExecutionTime = 0L;
          Double totalResourceUsed = 0D;
          Double totalInputBytesInBytes = 0D;

          for (AppResult appResult : results) {
            logger.info ("Job Execution Update: ApplicationID " + appResult.id);
            Long executionTime = appResult.finishTime - appResult.startTime - appResult.totalDelay;
            totalExecutionTime += executionTime;
            totalResourceUsed += appResult.resourceUsed;
            totalInputBytesInBytes += getTotalInputBytes (appResult);
          }

          if (totalExecutionTime != 0) {
            jobExecution.executionTime = totalExecutionTime * 1.0 / (1000 * 60);
            jobExecution.resourceUsage = totalResourceUsed * 1.0 / (1024 * 3600);
            jobExecution.inputSizeInBytes = totalInputBytesInBytes;
            logger.info ("Job Execution Update: UpdatedValue " + totalExecutionTime + ":" + totalResourceUsed + ":"
                + totalInputBytesInBytes);
          }

          Job job = jobExecution.job;

          // job id match and tuning enabled
          TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.select ("*")
              .fetch (TuningJobDefinition.TABLE.job, "*")
              .where ()
              .eq (TuningJobDefinition.TABLE.job + "." + Job.TABLE.id, job.id)
              .eq (TuningJobDefinition.TABLE.tuningEnabled, 1)
              .findUnique ();

          // todo: what if tuningJobDefinition is unique?

          logger.info ("Job execution " + jobExecution.resourceUsage);
          logger.info ("Job details: AvgResourceUsage " + tuningJobDefinition.averageResourceUsage + ", allowedMaxResourceUsagePercent: "
              + tuningJobDefinition.allowedMaxResourceUsagePercent);
          if (jobExecution.executionState.equals (JobExecution.ExecutionState.FAILED) || jobExecution.executionState.equals (JobExecution.ExecutionState.CANCELLED)) {
            // Todo: Check if the reason of failure is auto tuning and  handle cancelled cases
            tuningJobExecution.fitness =
                3 * tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent * 1024.0 * 1024.0 * 1024 / (100.0 * tuningJobDefinition.averageInputSizeInBytes);
          } else if (jobExecution.resourceUsage > (tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent / 100.0)) {
            tuningJobExecution.fitness =
                3 * tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent * 1024.0 * 1024.0 * 1024 / (100.0 * totalInputBytesInBytes );
          } else {
            tuningJobExecution.fitness = jobExecution.resourceUsage * 1024.0 * 1024.0 * 1024.0 / totalInputBytesInBytes;
          }
          tuningJobExecution.paramSetState = ParamSetStatus.FITNESS_COMPUTED;
          jobExecution.update ();
          tuningJobExecution.update ();

          logger.info("Completed executions after updating metrics: " + Json.toJson (tuningJobExecution));
        }
      } catch(Exception e){
        //String stackTrace = e.getStackTrace ().toString ();
        logger.error ("Error updating fitness of job_exec_id: " + tuningJobExecution.jobExecution.id + "\n Stacktrace: " , e);
      }
    }
    logger.debug("Execution metrics updated");
  }

  /**
   * Returns the total input size
   * @param appResult appResult
   * @return total input size
   */
  public Long getTotalInputBytes(AppResult appResult) {
    Long totalInputBytes = 0L;
    if (appResult.yarnAppHeuristicResults != null) {
      for (AppHeuristicResult appHeuristicResult : appResult.yarnAppHeuristicResults) {
        if (appHeuristicResult.heuristicName.equals("Mapper Speed")) {
          if (appHeuristicResult.yarnAppHeuristicResultDetails != null) {
            for (AppHeuristicResultDetails appHeuristicResultDetails : appHeuristicResult.yarnAppHeuristicResultDetails) {
              if (appHeuristicResultDetails.name.equals("Total input size in MB")) {
                totalInputBytes += Math.round(Double.parseDouble(appHeuristicResultDetails.value) * 1024 * 1024);
              }
            }
          }
        }
      }
    }
    return totalInputBytes;
  }
}
