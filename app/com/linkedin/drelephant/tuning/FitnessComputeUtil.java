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

import com.avaje.ebean.Expr;
import com.linkedin.drelephant.AutoTuner;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.mapreduce.heuristics.CommonConstantsHeuristic;
import com.linkedin.drelephant.util.Utils;
import controllers.AutoTuningMetricsController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import models.AppHeuristicResult;
import models.AppHeuristicResultDetails;
import models.AppResult;
import models.JobDefinition;
import models.JobExecution;
import models.JobSuggestedParamSet;
import models.JobSuggestedParamSet.ParamSetStatus;
import models.JobSuggestedParamValue;
import models.TuningAlgorithm;
import models.TuningJobDefinition;
import models.TuningJobExecutionParamSet;
import models.TuningParameter;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;


/**
 * This class computes the fitness of the suggested parameters after the execution is complete. This uses
 * Dr Elephant's DB to compute the fitness.
 * Fitness is : Resource Usage/(Input Size in GB)
 * In case there is failure or resource usage/execution time goes beyond configured limit, fitness is computed by
 * adding a penalty.
 */
public class FitnessComputeUtil {
  private static final Logger logger = Logger.getLogger(FitnessComputeUtil.class);
  private static final String FITNESS_COMPUTE_WAIT_INTERVAL = "fitness.compute.wait_interval.ms";
  private static final int MAX_TUNING_EXECUTIONS = 39;
  private static final int MIN_TUNING_EXECUTIONS = 18;
  private Long waitInterval;

  public FitnessComputeUtil() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    waitInterval = Utils.getNonNegativeLong(configuration, FITNESS_COMPUTE_WAIT_INTERVAL, 5 * AutoTuner.ONE_MIN);
  }

  private boolean isTuningEnabled(Integer jobDefinitionId) {
    TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.where()
        .eq(TuningJobDefinition.TABLE.job + '.' + JobDefinition.TABLE.id, jobDefinitionId)
        .order()
        // There can be multiple entries in tuningJobDefinition if the job is switch on/off multiple times.
        // The latest entry gives the information regarding whether tuning is enabled or not
        .desc(TuningJobDefinition.TABLE.createdTs)
        .setMaxRows(1)
        .findUnique();

    return tuningJobDefinition != null && tuningJobDefinition.tuningEnabled;
  }

  /**
   * Updates the metrics (execution time, resource usage, cost function) of the completed executions whose metrics are
   * not computed.
   */
  public void updateFitness() {
    logger.info("Computing and updating fitness for completed executions");
    List<TuningJobExecutionParamSet> completedJobExecutionParamSets = getCompletedJobExecutionParamSets();
    updateExecutionMetrics(completedJobExecutionParamSets);
    updateMetrics(completedJobExecutionParamSets);

    Set<JobDefinition> jobDefinitionSet = new HashSet<JobDefinition>();
    for (TuningJobExecutionParamSet  completedJobExecutionParamSet: completedJobExecutionParamSets) {
      JobDefinition jobDefinition = completedJobExecutionParamSet.jobSuggestedParamSet.jobDefinition;
      if (isTuningEnabled(jobDefinition.id)) {
        jobDefinitionSet.add(jobDefinition);
      }
    }
    checkToDisableTuning(jobDefinitionSet);
  }

  /**
   * Checks if the tuning parameters converge
   * @param tuningJobExecutionParamSets List of previous executions and corresponding param sets
   * @return true if the parameters converge, else false
   */
  private boolean didParameterSetConverge(List<TuningJobExecutionParamSet> tuningJobExecutionParamSets) {
    boolean result = false;
    int num_param_set_for_convergence = 3;

    if (tuningJobExecutionParamSets.size() < num_param_set_for_convergence) {
      return false;
    }

    TuningAlgorithm.JobType jobType = tuningJobExecutionParamSets.get(0).jobSuggestedParamSet.tuningAlgorithm.jobType;

    if (jobType == TuningAlgorithm.JobType.PIG) {

      Map<Integer, Set<Double>> paramValueSet = new HashMap<Integer, Set<Double>>();

      for (TuningJobExecutionParamSet tuningJobExecutionParamSet : tuningJobExecutionParamSets) {

        JobSuggestedParamSet jobSuggestedParamSet = tuningJobExecutionParamSet.jobSuggestedParamSet;
        List<JobSuggestedParamValue> jobSuggestedParamValueList = new ArrayList<JobSuggestedParamValue>();

        try {
          jobSuggestedParamValueList = JobSuggestedParamValue.find.where()
              .eq(JobSuggestedParamValue.TABLE.jobSuggestedParamSet + '.' + JobSuggestedParamSet.TABLE.id, jobSuggestedParamSet.id)
              .or(Expr.eq(JobSuggestedParamValue.TABLE.tuningParameter + '.' + TuningParameter.TABLE.paramName, "mapreduce.map.memory.mb"),
                  Expr.eq(JobSuggestedParamValue.TABLE.tuningParameter + '.' + TuningParameter.TABLE.paramName, "mapreduce.reduce.memory.mb"))
              .findList();
        } catch (NullPointerException e) {
          logger.info("Checking param convergence: Map memory and reduce memory parameter not found");
        }
        if (jobSuggestedParamValueList != null && jobSuggestedParamValueList.size() == 2) {
          num_param_set_for_convergence -= 1;
          for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValueList) {
            Set<Double> tmp;
            if (paramValueSet.containsKey(jobSuggestedParamValue.id)) {
              tmp = paramValueSet.get(jobSuggestedParamValue.id);
            } else {
              tmp = new HashSet<Double>();
            }
            tmp.add(jobSuggestedParamValue.paramValue);
            paramValueSet.put(jobSuggestedParamValue.id, tmp);
          }
        }

        if (num_param_set_for_convergence == 0) {
          break;
        }
      }

      result = true;
      for (Integer paramId : paramValueSet.keySet()) {
        if (paramValueSet.get(paramId).size() > 1) {
          result = false;
        }
      }
    }

    if (result) {
      logger.info(
          "Switching off tuning for job: " + tuningJobExecutionParamSets.get(0).jobSuggestedParamSet.jobDefinition.jobName + " Reason: parameter set converged");
    }
    return result;
  }

  /**
   * Checks if the median gain from tuning on last n executions is negative
   * @param tuningJobExecutionParamSets List of previous executions
   * @return true if the median gain is negative, else false
   */
  private boolean isMedianGainNegative(List<TuningJobExecutionParamSet> tuningJobExecutionParamSets) {
    int num_fitness_for_median = 6;
    Double[] fitnessArray = new Double[num_fitness_for_median];
    int entries = 0;

    if (tuningJobExecutionParamSets.size() < num_fitness_for_median) {
      return false;
    }
    for (TuningJobExecutionParamSet tuningJobExecutionParamSet : tuningJobExecutionParamSets) {
      JobSuggestedParamSet jobSuggestedParamSet = tuningJobExecutionParamSet.jobSuggestedParamSet;
      JobExecution jobExecution = tuningJobExecutionParamSet.jobExecution;
      if (jobExecution.executionState == JobExecution.ExecutionState.SUCCEEDED
          && jobSuggestedParamSet.paramSetState == ParamSetStatus.FITNESS_COMPUTED) {
        fitnessArray[entries] = jobSuggestedParamSet.fitness;
        entries += 1;
        if (entries == num_fitness_for_median) {
          break;
        }
      }
    }
    Arrays.sort(fitnessArray);
    double medianFitness;
    if (fitnessArray.length % 2 == 0) {
      medianFitness = (fitnessArray[fitnessArray.length / 2] + fitnessArray[fitnessArray.length / 2 - 1]) / 2;
    } else {
      medianFitness = fitnessArray[fitnessArray.length / 2];
    }

    JobDefinition jobDefinition = tuningJobExecutionParamSets.get(0).jobSuggestedParamSet.jobDefinition;
    TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.where().
        eq(TuningJobDefinition.TABLE.job + '.' + JobDefinition.TABLE.id, jobDefinition.id).findUnique();
    double baselineFitness =
        tuningJobDefinition.averageResourceUsage * FileUtils.ONE_GB / tuningJobDefinition.averageInputSizeInBytes;

    if (medianFitness > baselineFitness) {
      logger.info(
          "Switching off tuning for job: " + jobDefinition.jobName + " Reason: unable to tune enough");
      return true;
    } else {
      return false;
    }
  }

  /**
   * Switches off tuning for the given job
   * @param jobDefinition Job for which tuning is to be switched off
   */
  private void disableTuning(JobDefinition jobDefinition, String reason) {
    TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.where()
        .eq(TuningJobDefinition.TABLE.job + '.' + JobDefinition.TABLE.id, jobDefinition.id)
        .findUnique();
    if (tuningJobDefinition.tuningEnabled) {
      tuningJobDefinition.tuningEnabled = false;
      tuningJobDefinition.tuningDisabledReason = reason;
      tuningJobDefinition.save();
    }
  }

  /**
   * Checks and disables tuning for the given job definitions.
   * Tuning can be disabled if:
   *  - Number of tuning executions >=  MAX_TUNING_EXECUTIONS
   *  - or number of tuning executions >= MIN_TUNING_EXECUTIONS and parameters converge
   *  - or number of tuning executions >= MIN_TUNING_EXECUTIONS and median gain (in cost function) in last 6 executions is negative
   * @param jobDefinitionSet Set of jobs to check if tuning can be switched off for them
   */
  private void checkToDisableTuning(Set<JobDefinition> jobDefinitionSet) {
    for (JobDefinition jobDefinition : jobDefinitionSet) {
      try {
        List<TuningJobExecutionParamSet> tuningJobExecutionParamSets = TuningJobExecutionParamSet.find
            .fetch(TuningJobExecutionParamSet.TABLE.jobSuggestedParamSet, "*")
            .fetch(TuningJobExecutionParamSet.TABLE.jobExecution, "*")
            .where()
            .eq(TuningJobExecutionParamSet.TABLE.jobSuggestedParamSet + '.'
                + JobSuggestedParamSet.TABLE.jobDefinition + '.' + JobDefinition.TABLE.id, jobDefinition.id)
            .order()
            .desc("job_execution_id")
            .findList();


        if (tuningJobExecutionParamSets.size() >= MIN_TUNING_EXECUTIONS) {
          if (didParameterSetConverge(tuningJobExecutionParamSets)) {
            logger.info("Parameters converged. Disabling tuning for job: " + jobDefinition.jobName);
            disableTuning(jobDefinition, "Parameters converged");
          } else if (isMedianGainNegative(tuningJobExecutionParamSets)) {
            logger.info("Unable to get gain while tuning. Disabling tuning for job: " + jobDefinition.jobName);
            disableTuning(jobDefinition, "Unable to get gain");
          } else if (tuningJobExecutionParamSets.size() >= MAX_TUNING_EXECUTIONS) {
            logger.info("Maximum tuning executions limit reached. Disabling tuning for job: " + jobDefinition.jobName);
            disableTuning(jobDefinition, "Maximum executions reached");
          }
        }
      } catch (NullPointerException e) {
        logger.info("No execution found for job: " + jobDefinition.jobName);
      }
    }
  }

  /**
   * This method update metrics for auto tuning monitoring for fitness compute daemon
   * @param completedJobExecutionParamSets List of completed tuning job executions
   */
  private void updateMetrics(List<TuningJobExecutionParamSet> completedJobExecutionParamSets) {
    int fitnessNotUpdated = 0;
    for (TuningJobExecutionParamSet completedJobExecutionParamSet : completedJobExecutionParamSets) {
      if (!completedJobExecutionParamSet.jobSuggestedParamSet.paramSetState.equals(ParamSetStatus.FITNESS_COMPUTED)) {
        fitnessNotUpdated++;
      } else {
        AutoTuningMetricsController.markFitnessComputedJobs();
      }
    }
    AutoTuningMetricsController.setFitnessComputeWaitJobs(fitnessNotUpdated);
  }

  /**
   * Returns the list of completed executions whose metrics are not computed
   * @return List of job execution
   */
  private List<TuningJobExecutionParamSet> getCompletedJobExecutionParamSets() {
    logger.info("Fetching completed executions whose fitness are yet to be computed");
    List<TuningJobExecutionParamSet> completedJobExecutionParamSet = new ArrayList<TuningJobExecutionParamSet>();

    try {
      List<TuningJobExecutionParamSet> tuningJobExecutionParamSets = TuningJobExecutionParamSet.find.select("*")
          .fetch(TuningJobExecutionParamSet.TABLE.jobExecution, "*")
          .fetch(TuningJobExecutionParamSet.TABLE.jobSuggestedParamSet, "*")
          .where()
          .eq(TuningJobExecutionParamSet.TABLE.jobSuggestedParamSet + "." + JobSuggestedParamSet.TABLE.paramSetState,
              ParamSetStatus.EXECUTED)
          .findList();


      for (TuningJobExecutionParamSet tuningJobExecutionParamSet : tuningJobExecutionParamSets) {
        JobExecution jobExecution = tuningJobExecutionParamSet.jobExecution;
        long diff = System.currentTimeMillis() - jobExecution.updatedTs.getTime();
        logger.debug("Current Time in millis: " + System.currentTimeMillis() + ", Job execution last updated time "
            + jobExecution.updatedTs.getTime());
        if (diff < waitInterval) {
          logger.debug("Delaying fitness compute for execution: " + jobExecution.jobExecId);
        } else {
          logger.debug("Adding execution " + jobExecution.jobExecId + " for fitness computation");
          completedJobExecutionParamSet.add(tuningJobExecutionParamSet);
        }
      }
    } catch (NullPointerException e) {
      logger.error("No completed executions found for which fitness is to be computed", e);
    }
    logger.info("Number of completed execution fetched for fitness computation: " + completedJobExecutionParamSet.size());
    return completedJobExecutionParamSet;
  }

  /**
   * Updates the execution metrics
   * @param completedJobExecutionParamSets List of completed executions
   */
  private void updateExecutionMetrics(List<TuningJobExecutionParamSet> completedJobExecutionParamSets) {
    Integer penaltyConstant = 3;

    for (TuningJobExecutionParamSet completedJobExecutionParamSet : completedJobExecutionParamSets) {

      JobExecution jobExecution = completedJobExecutionParamSet.jobExecution;
      JobSuggestedParamSet jobSuggestedParamSet = completedJobExecutionParamSet.jobSuggestedParamSet;
      JobDefinition job = jobExecution.job;

      logger.info("Updating execution metrics and fitness for execution: " + jobExecution.jobExecId);
      try {

        TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.select("*")
            .fetch(TuningJobDefinition.TABLE.job, "*")
            .where()
            .eq(TuningJobDefinition.TABLE.job + "." + JobDefinition.TABLE.id, job.id)
            .order()
            .desc(TuningJobDefinition.TABLE.createdTs)
            .findUnique();

        List<AppResult> results = AppResult.find.select("*")
            .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
            .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
                "*")
            .where()
            .eq(AppResult.TABLE.FLOW_EXEC_ID, jobExecution.flowExecution.flowExecId)
            .eq(AppResult.TABLE.JOB_EXEC_ID, jobExecution.jobExecId)
            .findList();

        if (results != null && results.size() > 0) {
          Double totalResourceUsed = 0D;
          Double totalInputBytesInBytes = 0D;

          for (AppResult appResult : results) {
            totalResourceUsed += appResult.resourceUsed;
            totalInputBytesInBytes += getTotalInputBytes(appResult);
          }

          Long totalRunTime = Utils.getTotalRuntime(results);
          Long totalDelay = Utils.getTotalWaittime(results);
          Long totalExecutionTime = totalRunTime - totalDelay;

          if (totalExecutionTime != 0) {
            jobExecution.executionTime = totalExecutionTime * 1.0 / (1000 * 60);
            jobExecution.resourceUsage = totalResourceUsed * 1.0 / (1024 * 3600);
            jobExecution.inputSizeInBytes = totalInputBytesInBytes;

            logger.info(
                "Metric Values for execution " + jobExecution.jobExecId + ": Execution time = " + totalExecutionTime
                    + ", Resource usage = " + totalResourceUsed + " and total input size = " + totalInputBytesInBytes);
          }

          if (tuningJobDefinition.averageResourceUsage == null && totalExecutionTime != 0) {
            tuningJobDefinition.averageResourceUsage = jobExecution.resourceUsage;
            tuningJobDefinition.averageExecutionTime = jobExecution.executionTime;
            tuningJobDefinition.averageInputSizeInBytes = jobExecution.inputSizeInBytes.longValue();
            tuningJobDefinition.update();
          }

          //Compute fitness
          Double averageResourceUsagePerGBInput =
              tuningJobDefinition.averageResourceUsage * FileUtils.ONE_GB / tuningJobDefinition.averageInputSizeInBytes;
          Double maxDesiredResourceUsagePerGBInput =
              averageResourceUsagePerGBInput * tuningJobDefinition.allowedMaxResourceUsagePercent / 100.0;
          Double averageExecutionTimePerGBInput =
              tuningJobDefinition.averageExecutionTime * FileUtils.ONE_GB / tuningJobDefinition.averageInputSizeInBytes;
          Double maxDesiredExecutionTimePerGBInput =
              averageExecutionTimePerGBInput * tuningJobDefinition.allowedMaxExecutionTimePercent / 100.0;
          Double resourceUsagePerGBInput =
              jobExecution.resourceUsage * FileUtils.ONE_GB / jobExecution.inputSizeInBytes;
          Double executionTimePerGBInput =
              jobExecution.executionTime * FileUtils.ONE_GB / jobExecution.inputSizeInBytes;

          if (resourceUsagePerGBInput > maxDesiredResourceUsagePerGBInput
              || executionTimePerGBInput > maxDesiredExecutionTimePerGBInput) {
            logger.info("Execution " + jobExecution.jobExecId + " violates constraint on resource usage per GB input");
            jobSuggestedParamSet.fitness = penaltyConstant * maxDesiredResourceUsagePerGBInput;
          } else {
            jobSuggestedParamSet.fitness = resourceUsagePerGBInput;
          }
          jobSuggestedParamSet.paramSetState = ParamSetStatus.FITNESS_COMPUTED;
          jobSuggestedParamSet.fitnessJobExecution = jobExecution;
          jobExecution.update();
          jobSuggestedParamSet.update();
        }

        JobSuggestedParamSet currentBestJobSuggestedParamSet;
        try {
          currentBestJobSuggestedParamSet = JobSuggestedParamSet.find.where()
              .eq(JobSuggestedParamSet.TABLE.jobDefinition + "." + JobDefinition.TABLE.id,
                  jobSuggestedParamSet.jobDefinition.id)
              .eq(JobSuggestedParamSet.TABLE.isParamSetBest, 1)
              .findUnique();
          if (currentBestJobSuggestedParamSet.fitness > jobSuggestedParamSet.fitness) {
            currentBestJobSuggestedParamSet.isParamSetBest = false;
            jobSuggestedParamSet.isParamSetBest = true;
            currentBestJobSuggestedParamSet.save();
            jobSuggestedParamSet.save();
          }
        } catch (NullPointerException e) {
          jobSuggestedParamSet.isParamSetBest = true;
          jobSuggestedParamSet.save();
        }
      } catch (Exception e) {
        logger.error("Error updating fitness of execution: " + jobExecution.id + "\n Stacktrace: ",
            e);
      }
    }
    logger.info("Execution metrics updated");
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
