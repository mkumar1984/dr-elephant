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

import models.AppHeuristicResult;
import models.AppHeuristicResultDetails;
import models.AppResult;
import models.JobDefinition;
import models.JobExecution;
import models.TuningJobDefinition;
import models.TuningJobExecution;
import models.TuningJobExecution.ParamSetStatus;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import play.libs.Json;

import com.linkedin.drelephant.AutoTuner;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.mapreduce.heuristics.CommonConstantsHeuristic;
import com.linkedin.drelephant.util.Utils;


/**
 * This class computes the fitness of the suggested parameters after the execution is complete. This uses
 * Dr Elephant's DB to compute the fitness.
 * Fitness is : Resource Usage/Input Size in GB
 * In case there is failure or resource usage/execution time goes beyond configured limit, fitness is computed by
 * adding a penalty.
 */
public class FitnessComputeUtil {
  private static final Logger logger = Logger.getLogger(FitnessComputeUtil.class);
  private static final String FITNESS_COMPUTE_WAIT_INTERVAL = "fitness.compute.wait_interval.ms";
  private Long waitInterval;

  public FitnessComputeUtil() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    waitInterval = Utils.getNonNegativeLong(configuration, FITNESS_COMPUTE_WAIT_INTERVAL, 5 * AutoTuner.ONE_MIN);
  }

  /**
   * Updates the metrics (execution time, resource usage, cost function) of the completed executions whose metrics are
   * not computed.
   * @return List of job execution
   */
  public List<TuningJobExecution> updateFitness() {
    logger.info("Updating fitness");
    List<TuningJobExecution> completedExecutions = getCompletedExecutions();
    updateExecutionMetrics(completedExecutions);
    logger.info("Fitness updated");
    return completedExecutions;
  }

  /**
   * Returns the list of completed executions whose metrics are not computed
   * @return List of job execution
   */
  private List<TuningJobExecution> getCompletedExecutions() {
    logger.debug("Inside getCompletedExecutions jobs");
    List<TuningJobExecution> jobExecutions = new ArrayList<TuningJobExecution>();
    List<TuningJobExecution> outputJobExecutions = new ArrayList<TuningJobExecution>();

    try {
      jobExecutions = TuningJobExecution.find.select("*")
          .where()
          .eq(TuningJobExecution.TABLE.paramSetState, ParamSetStatus.EXECUTED)
          .findList();

      for (TuningJobExecution tuningJobExecution : jobExecutions) {
        long diff = System.currentTimeMillis() - tuningJobExecution.jobExecution.updatedTs.getTime();
        logger.debug("CurrentMillis " + System.currentTimeMillis() + ", Job update time "
            + tuningJobExecution.jobExecution.updatedTs.getTime());
        if (diff < waitInterval) {
          logger.debug("Delaying fitness compute for job " + tuningJobExecution.jobExecution.jobExecId);
        } else {
          logger.debug("Adding job for fitness compute " + tuningJobExecution.jobExecution.jobExecId);
          outputJobExecutions.add(tuningJobExecution);
        }
      }
    } catch (NullPointerException e) {
      logger.error("No completed executions found for computing fitness", e);
    }
    logger.debug("Finished getCompletedExecutions jobs");
    return outputJobExecutions;
  }

  /**
   * Updates the execution metrics
   * @param completedExecutions List of completed executions
   */
  protected void updateExecutionMetrics(List<TuningJobExecution> completedExecutions) {
    logger.debug("Updating execution metrics");
    for (TuningJobExecution tuningJobExecution : completedExecutions) {

      logger.debug("Completed executions before updating metric: " + Json.toJson(tuningJobExecution));

      try {
        JobExecution jobExecution = tuningJobExecution.jobExecution;
        JobDefinition job = jobExecution.job;

        // job id match and tuning enabled
        TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.select("*")
            .fetch(TuningJobDefinition.TABLE.job, "*")
            .where()
            .eq(TuningJobDefinition.TABLE.job + "." + JobDefinition.TABLE.id, job.id)
            .eq(TuningJobDefinition.TABLE.tuningEnabled, 1)
            .findUnique();

        logger.debug("Job Execution Update: Flow Execution ID " + jobExecution.flowExecution.flowExecId + " Job ID "
            + jobExecution.jobExecId);

        List<AppResult> results = AppResult.find.select("*")
            .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
            .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
                "*")
            .where()
            .eq(AppResult.TABLE.FLOW_EXEC_ID, jobExecution.flowExecution.flowExecId)
            .eq(AppResult.TABLE.JOB_EXEC_ID, jobExecution.jobExecId)
            .findList();

        if (results != null && results.size() > 0) {
          Long totalExecutionTime = 0L;
          Double totalResourceUsed = 0D;
          Double totalInputBytesInBytes = 0D;

          for (AppResult appResult : results) {
            logger.info("Job Execution Update: ApplicationID " + appResult.id);
            totalResourceUsed += appResult.resourceUsed;
            totalInputBytesInBytes += getTotalInputBytes(appResult);
          }

          Long totalRunTime = Utils.getTotalRuntime(results);
          Long totalDelay = Utils.getTotalWaittime(results);
          totalExecutionTime = totalRunTime - totalDelay;

          if (totalExecutionTime != 0) {
            jobExecution.executionTime = totalExecutionTime * 1.0 / (1000 * 60);
            jobExecution.resourceUsage = totalResourceUsed * 1.0 / (1024 * 3600);
            jobExecution.inputSizeInBytes = totalInputBytesInBytes;
            logger.info("Job Execution Update: UpdatedValue " + totalExecutionTime + ":" + totalResourceUsed + ":"
                + totalInputBytesInBytes);
          }

          logger.debug("Job execution " + jobExecution.resourceUsage);
          logger.debug("Job details: AvgResourceUsage " + tuningJobDefinition.averageResourceUsage
              + ", allowedMaxResourceUsagePercent: " + tuningJobDefinition.allowedMaxResourceUsagePercent);

          //Compute fitness
          if (jobExecution.executionState.equals(JobExecution.ExecutionState.FAILED)
              || jobExecution.executionState.equals(JobExecution.ExecutionState.CANCELLED)) {
            // Todo: Check if the reason of failure is auto tuning and  handle cancelled cases
            tuningJobExecution.fitness =
                3 * tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent
                    * FileUtils.ONE_GB / (100.0 * tuningJobDefinition.averageInputSizeInBytes);
          } else if (jobExecution.resourceUsage > (
              tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent / 100.0)) {
            tuningJobExecution.fitness =
                3 * tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent
                    * FileUtils.ONE_GB / (100.0 * totalInputBytesInBytes);
          } else {
            tuningJobExecution.fitness = jobExecution.resourceUsage * FileUtils.ONE_GB / totalInputBytesInBytes;
          }
          tuningJobExecution.paramSetState = ParamSetStatus.FITNESS_COMPUTED;
          jobExecution.update();
          tuningJobExecution.update();

          logger.debug("Completed executions after updating metrics: " + Json.toJson(tuningJobExecution));
        } else {
          if (jobExecution.executionState.equals(JobExecution.ExecutionState.FAILED)
              || jobExecution.executionState.equals(JobExecution.ExecutionState.CANCELLED)) {
            // Todo: Check if the reason of failure is auto tuning and  handle cancelled cases
            tuningJobExecution.fitness =
                3 * tuningJobDefinition.averageResourceUsage * tuningJobDefinition.allowedMaxResourceUsagePercent
                    * FileUtils.ONE_GB / (100.0 * tuningJobDefinition.averageInputSizeInBytes);
            jobExecution.executionTime = 0D;
            jobExecution.resourceUsage = 0D;
            jobExecution.inputSizeInBytes = 0D;
            tuningJobExecution.paramSetState = ParamSetStatus.FITNESS_COMPUTED;
            jobExecution.update();
            tuningJobExecution.update();
          }
        }
      } catch (Exception e) {
        //String stackTrace = e.getStackTrace ().toString ();
        logger.error("Error updating fitness of job_exec_id: " + tuningJobExecution.jobExecution.id + "\n Stacktrace: ",
            e);
      }
    }
    logger.debug("Execution metrics updated");
  }

  /**
   * Returns the total input size
   * @param appResult appResult
   * @return total input size
   */
  private Long getTotalInputBytes(AppResult appResult) {
    Long totalInputBytes = 0L;
    if (appResult.yarnAppHeuristicResults != null) {
      for (AppHeuristicResult appHeuristicResult : appResult.yarnAppHeuristicResults) {
        if (appHeuristicResult.heuristicName.equals(CommonConstantsHeuristic.MAPPER_SPEED)) {
          if (appHeuristicResult.yarnAppHeuristicResultDetails != null) {
            for (AppHeuristicResultDetails appHeuristicResultDetails : appHeuristicResult.yarnAppHeuristicResultDetails) {
              if (appHeuristicResultDetails.name.equals(CommonConstantsHeuristic.TOTAL_INPUT_SIZE_IN_MB)) {
                totalInputBytes += Math.round(Double.parseDouble(appHeuristicResultDetails.value) * FileUtils.ONE_MB);
              }
            }
          }
        }
      }
    }
    return totalInputBytes;
  }
}
