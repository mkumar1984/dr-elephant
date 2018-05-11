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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.util.Utils;
import controllers.AutoTuningMetricsController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.FlowDefinition;
import models.FlowExecution;
import models.JobDefinition;
import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.JobSuggestedParamSet;
import models.JobSuggestedParamValue;
import models.TuningAlgorithm;
import models.TuningJobDefinition;
import models.JobSuggestedParamSet.ParamSetStatus;
import models.TuningParameter;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;


/**
 * This class processes the API requests and returns param suggestion as response
 */
public class AutoTuningAPIHelper {

  public static final String ALLOWED_MAX_RESOURCE_USAGE_PERCENT_DEFAULT =
      "autotuning.default.allowed_max_resource_usage_percent";
  public static final String ALLOWED_MAX_EXECUTION_TIME_PERCENT_DEFAULT =
      "autotuning.default.allowed_max_execution_time_percent";
  private static final Logger logger = Logger.getLogger(AutoTuningAPIHelper.class);

  /**
   * For a job, returns the execution with the best parameter set if available else  the one with the default parameter set.
   * @param jobDefId Sting JobDefId of the job
   * @return JobSuggestedParamSet with the best parameter set if available else  the one with the default parameter set.
   */

  private JobSuggestedParamSet getBestParamSetTuningJobExecution(String jobDefId) {
    JobSuggestedParamSet jobSuggestedParamSetBestParamSet = JobSuggestedParamSet.find.select("*")
        .where()
        .eq(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + JobDefinition.TABLE.jobDefId,
            jobDefId)
        .eq(JobSuggestedParamSet.TABLE.isParamSetBest, true)
        .setMaxRows(1)
        .findUnique();

    if (jobSuggestedParamSetBestParamSet == null) {
      jobSuggestedParamSetBestParamSet = JobSuggestedParamSet.find.select("*")
          .where()
          .eq(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + JobDefinition.TABLE.jobDefId,
              jobDefId)
          .eq(JobSuggestedParamSet.TABLE.isParamSetDefault, true)
          .setMaxRows(1)
          .findUnique();
    }
    return jobSuggestedParamSetBestParamSet;
  }

  /**
   * Returns the param set corresponding to a given job execution id
   * @param jobExecutionId Long job execution id of the execution
   * @return List<JobSuggestedParamValue>: list of parameters
   */
  private List<JobSuggestedParamValue> getJobExecutionParamSet(Long jobExecutionId) {
    return JobSuggestedParamValue.find.where()
        .eq(JobSuggestedParamValue.TABLE.jobSuggestedParamSet + "." + JobExecution.TABLE.id, jobExecutionId)
        .findList();
  }

  /**
   * This method creates a job execution with best parameter set. This is required when there is no parameters set or tuning has been switch off for the job
   * remains for suggestion.
   * @param tuningJobDefinition Job definition
   * @return Tuning Job Execution with best parameters
   */
  private JobSuggestedParamSet cloneBestParamSetTuningJobExecution(TuningJobDefinition tuningJobDefinition) {
    logger.info("Searching for best param set for job: " + tuningJobDefinition.job.jobName);

    JobSuggestedParamSet bestParamSetJobSuggestedParamSet =
        getBestParamSetTuningJobExecution(tuningJobDefinition.job.jobDefId);
    List<JobSuggestedParamValue> jobSuggestedParamValueList =
        getJobExecutionParamSet(bestParamSetJobSuggestedParamSet.jobExecution.id);

    JobSuggestedParamSet jobSuggestedParamSet = new JobSuggestedParamSet();
    JobExecution jobExecution = new JobExecution();
    jobExecution.id = 0L;
    jobExecution.job = bestParamSetJobSuggestedParamSet.jobExecution.job;
    jobExecution.executionState = ExecutionState.NOT_STARTED;
    jobExecution.save();

    jobSuggestedParamSet.jobExecution = jobExecution;
    jobSuggestedParamSet.isParamSetDefault = bestParamSetJobSuggestedParamSet.isParamSetDefault;
    jobSuggestedParamSet.tuningAlgorithm = bestParamSetJobSuggestedParamSet.tuningAlgorithm;
    jobSuggestedParamSet.paramSetState = ParamSetStatus.CREATED;
    jobSuggestedParamSet.save();

    logger.debug("Execution with default parameter created with execution id: " + jobSuggestedParamSet.jobExecution.id);

    //Save default parameters corresponding to new default execution
    for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValueList) {
      JobSuggestedParamValue jobSuggestedParamValue1 = new JobSuggestedParamValue();
      jobSuggestedParamValue1.id = 0;
      jobSuggestedParamValue1.jobExecution = jobExecution;
      jobSuggestedParamValue1.paramValue = jobSuggestedParamValue.paramValue;
      jobSuggestedParamValue1.tuningParameter = jobSuggestedParamValue.tuningParameter;
      jobSuggestedParamValue1.save();
    }

    jobSuggestedParamSet = JobSuggestedParamSet.find.select("*")
        .where()
        .eq(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.id, jobSuggestedParamSet.jobExecution.id)
        .setMaxRows(1)
        .findUnique();

    return jobSuggestedParamSet;
  }

  /**
   * Sets the max allowed increase percentage for metrics: execution time and resource usage if not provided in API call
   * @param tuningInput TuningInput
   */
  private void setMaxAllowedMetricIncreasePercentage(TuningInput tuningInput) {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    if (tuningInput.getAllowedMaxExecutionTimePercent() == null) {
      Double allowedMaxExecutionTimePercent =
          new Double(Utils.getNonNegativeInt(configuration, ALLOWED_MAX_EXECUTION_TIME_PERCENT_DEFAULT, 150));
      tuningInput.setAllowedMaxExecutionTimePercent(allowedMaxExecutionTimePercent);
    }
    if (tuningInput.getAllowedMaxResourceUsagePercent() == null) {
      Double allowedMaxResourceUsagePercent =
          new Double(Utils.getNonNegativeInt(configuration, ALLOWED_MAX_RESOURCE_USAGE_PERCENT_DEFAULT, 150));
      tuningInput.setAllowedMaxResourceUsagePercent(allowedMaxResourceUsagePercent);
    }
  }

  /**
   * Sets the tuning algorithm based on the job type and optimization metric
   * @param tuningInput TuningInput for which tuning algorithm is to be set
   */
  private void setTuningAlgorithm(TuningInput tuningInput) {
    //Todo: Handle algorithm version later
    TuningAlgorithm tuningAlgorithm = TuningAlgorithm.find.select("*")
        .where()
        .eq(TuningAlgorithm.TABLE.jobType, tuningInput.getJobType())
        .eq(TuningAlgorithm.TABLE.optimizationMetric, tuningInput.getOptimizationMetric())
        .findUnique();
    tuningInput.setTuningAlgorithm(tuningAlgorithm);
  }

  /**
   * Applies penalty to the given execution
   * @param jobExecId String jobExecId of the execution to which penalty has to be applied
   */
  private void applyPenalty(String jobExecId) {
    Integer penaltyConstant = 3;
    logger.info("Execution " + jobExecId + " failed/cancelled. Applying penalty");
    JobSuggestedParamSet jobSuggestedParamSet = JobSuggestedParamSet.find.where()
        .eq(JobSuggestedParamSet.TABLE.jobExecution + '.' + JobExecution.TABLE.jobExecId, jobExecId)
        .findUnique();
    JobDefinition jobDefinition = jobSuggestedParamSet.jobExecution.job;
    TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.where()
        .eq(TuningJobDefinition.TABLE.job + '.' + JobDefinition.TABLE.id, jobDefinition.id)
        .findUnique();
    Double averageResourceUsagePerGBInput =
        tuningJobDefinition.averageResourceUsage * FileUtils.ONE_GB / tuningJobDefinition.averageInputSizeInBytes;
    Double maxDesiredResourceUsagePerGBInput =
        averageResourceUsagePerGBInput * tuningJobDefinition.allowedMaxResourceUsagePercent / 100.0;
    jobSuggestedParamSet.fitness = penaltyConstant * maxDesiredResourceUsagePerGBInput;
    jobSuggestedParamSet.paramSetState = ParamSetStatus.FITNESS_COMPUTED;
    jobSuggestedParamSet.update();

    JobExecution jobExecution = jobSuggestedParamSet.jobExecution;
    jobExecution.resourceUsage = 0D;
    jobExecution.executionTime = 0D;
    jobExecution.inputSizeInBytes = 1D;
    jobExecution.save();
  }

  /**
   * Handles the api request and returns param suggestions as response
   * @param tuningInput Rest api parameters
   * @return Parameter Suggestion
   */
  public Map<String, Double> getCurrentRunParameters(TuningInput tuningInput) {
    logger.info("Parameter suggestion request for execution: " + tuningInput.getJobExecId());
    List<JobSuggestedParamValue> jobSuggestedParamValues;

    if (tuningInput.getAllowedMaxExecutionTimePercent() == null
        || tuningInput.getAllowedMaxResourceUsagePercent() == null) {
      setMaxAllowedMetricIncreasePercentage(tuningInput);
    }
    setTuningAlgorithm(tuningInput);
    String jobDefId = tuningInput.getJobDefId();

    if (tuningInput.getRetry()) {
      applyPenalty(tuningInput.getJobExecId());
      JobSuggestedParamSet bestParamSetJobSuggestedParamSet = getBestParamSetTuningJobExecution(jobDefId);
      jobSuggestedParamValues = getJobExecutionParamSet(bestParamSetJobSuggestedParamSet.jobExecution.id);
    } else {
      boolean isJobNewToTuning = false;
      boolean isTuningEnabledForJob;

      TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.select("*")
          .fetch(TuningJobDefinition.TABLE.job, "*")
          .where()
          .eq(TuningJobDefinition.TABLE.job + "." + JobDefinition.TABLE.jobDefId, jobDefId)
          .eq(TuningJobDefinition.TABLE.tuningEnabled, 1)
          .findUnique();

      isTuningEnabledForJob = tuningJobDefinition != null;

      if (!isTuningEnabledForJob) {
        //Tuning not enabled for the job currently. Either the job is new to tuning or tuning has been turned off for the job
        //TuningJobDefinition will have a unique entry for every time a job is turned on for tuning
        tuningJobDefinition = TuningJobDefinition.find.select("*")
            .fetch(TuningJobDefinition.TABLE.job, "*")
            .where()
            .eq(TuningJobDefinition.TABLE.job + "." + JobDefinition.TABLE.jobDefId, jobDefId)
            .setMaxRows(1)
            .orderBy(TuningJobDefinition.TABLE.createdTs + " desc")
            .findUnique();

        isJobNewToTuning = tuningJobDefinition == null;

        if (isJobNewToTuning) {
          //The job is new to tuning
          logger.debug("Registering job: " + tuningInput.getJobName() + " for auto tuning tuning");
          AutoTuningMetricsController.markNewAutoTuningJob();
          tuningJobDefinition = addNewJobForTuning(tuningInput);
        }
      }

      JobSuggestedParamSet jobSuggestedParamSet;
      if (isJobNewToTuning || isTuningEnabledForJob) {
        logger.debug("Finding parameter suggestion for job: " + tuningJobDefinition.job.jobName);
        jobSuggestedParamSet = getNewTuningJobExecution(tuningJobDefinition);
      } else {
        //Tuning has been switched off for the job. Returning best param set
        jobSuggestedParamSet = cloneBestParamSetTuningJobExecution(tuningJobDefinition);
      }
      updateJobExecutionParameter(jobSuggestedParamSet, tuningInput);
      jobSuggestedParamValues = getJobExecutionParamSet(jobSuggestedParamSet.jobExecution.id);
    }
    logger.debug("Number of output parameters : " + jobSuggestedParamValues.size());
    logger.info("Finishing getCurrentRunParameters");
    return jobSuggestedParamValueListToMap(jobSuggestedParamValues);
  }

  /**
   * Returns an execution with unsent parameters corresponding to the given job definition
   * @param tuningJobDefinition TuningJobDefinition corresponding to which execution is to be returned
   * @return JobSuggestedParamSet corresponding to the given job definition
   */
  private JobSuggestedParamSet getNewTuningJobExecution(TuningJobDefinition tuningJobDefinition) {
    JobSuggestedParamSet jobSuggestedParamSet = JobSuggestedParamSet.find.select("*")
        .fetch(JobSuggestedParamSet.TABLE.jobExecution, "*")
        .fetch(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.job, "*")
        .where()
        .eq(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + JobDefinition.TABLE.id,
            tuningJobDefinition.job.id)
        .eq(JobSuggestedParamSet.TABLE.paramSetState, ParamSetStatus.CREATED)
        .order()
        .asc(JobSuggestedParamSet.TABLE.jobExecution + "." + JobExecution.TABLE.createdTs)
        .setMaxRows(1)
        .findUnique();

    //If no new parameter set for suggestion, create a new suggestion with best parameter set
    if (jobSuggestedParamSet == null) {
      logger.info(
          "Returning best parameter set as no parameter suggestion found for job: " + tuningJobDefinition.job.jobName);
      AutoTuningMetricsController.markParamSetNotFound();
      jobSuggestedParamSet = cloneBestParamSetTuningJobExecution(tuningJobDefinition);
    }
    return jobSuggestedParamSet;
  }

  /**
   * Returns the list of JobSuggestedParamValue as Map of String to Double
   * @param jobSuggestedParamValues List of JobSuggestedParamValue
   * @return Map of string to double containing the parameter name and corresponding value
   */
  private Map<String, Double> jobSuggestedParamValueListToMap(List<JobSuggestedParamValue> jobSuggestedParamValues) {
    Map<String, Double> paramValues = new HashMap<String, Double>();
    if (jobSuggestedParamValues != null) {
      for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValues) {
        logger.debug("Param Name is " + jobSuggestedParamValue.tuningParameter.paramName + " And value is "
            + jobSuggestedParamValue.paramValue);
        paramValues.put(jobSuggestedParamValue.tuningParameter.paramName, jobSuggestedParamValue.paramValue);
      }
    }
    return paramValues;
  }

  /**
   *This is to update job execution with IN_PROGRESS and parameter set with IN_PROGRESS. Also update flow_exec_id
   *, flowExecURL, JobExecID and jobExecURL
   * @param jobSuggestedParamSet JobSuggestedParamSet which is to be updated
   * @param tuningInput TuningInput corresponding to the JobSuggestedParamSet
   */
  private void updateJobExecutionParameter(JobSuggestedParamSet jobSuggestedParamSet, TuningInput tuningInput) {

    FlowExecution flowExecution =
        FlowExecution.find.where().eq(FlowExecution.TABLE.flowExecId, tuningInput.getFlowExecId()).findUnique();

    if (flowExecution == null) {
      flowExecution = new FlowExecution();
      flowExecution.flowExecId = tuningInput.getFlowExecId();
      flowExecution.flowExecUrl = tuningInput.getFlowExecUrl();
      flowExecution.flowDefinition = jobSuggestedParamSet.jobExecution.job.flowDefinition;
      flowExecution.save();
    }

    JobExecution jobExecution = jobSuggestedParamSet.jobExecution;
    jobExecution.jobExecId = tuningInput.getJobExecId();
    jobExecution.jobExecUrl = tuningInput.getJobExecUrl();
    jobExecution.executionState = ExecutionState.IN_PROGRESS;
    jobExecution.flowExecution = flowExecution;

    logger.debug("Saving job execution" + jobExecution.jobExecId);

    jobExecution.save();

    jobSuggestedParamSet.jobExecution = jobExecution;
    jobSuggestedParamSet.paramSetState = ParamSetStatus.SENT;
    jobSuggestedParamSet.save();
  }

  /**
   * Add new job for tuning
   * @param tuningInput Tuning input parameters
   * @return Job
   */
  private TuningJobDefinition addNewJobForTuning(TuningInput tuningInput) {

    logger.info("Adding new job for tuning, job id: " + tuningInput.getJobDefId());

    JobDefinition job =
        JobDefinition.find.select("*").where().eq(JobDefinition.TABLE.jobDefId, tuningInput.getJobDefId()).findUnique();

    FlowDefinition flowDefinition =
        FlowDefinition.find.where().eq(FlowDefinition.TABLE.flowDefId, tuningInput.getFlowDefId()).findUnique();

    if (flowDefinition == null) {
      flowDefinition = new FlowDefinition();
      flowDefinition.flowDefId = tuningInput.getFlowDefId();
      flowDefinition.flowDefUrl = tuningInput.getFlowDefUrl();
      flowDefinition.save();
    }

    if (job == null) {
      job = new JobDefinition();
      job.jobDefId = tuningInput.getJobDefId();
      job.scheduler = tuningInput.getScheduler();
      job.username = tuningInput.getUserName();
      job.jobName = tuningInput.getJobName();
      job.jobDefUrl = tuningInput.getJobDefUrl();
      job.flowDefinition = flowDefinition;
      job.save();
    }

    String flowExecId = tuningInput.getFlowExecId();
    String jobExecId = tuningInput.getJobExecId();
    String flowExecUrl = tuningInput.getFlowExecUrl();
    String jobExecUrl = tuningInput.getJobExecUrl();
    String client = tuningInput.getClient();
    String defaultParams = tuningInput.getDefaultParams();

    TuningJobDefinition tuningJobDefinition = new TuningJobDefinition();
    tuningJobDefinition.job = job;
    tuningJobDefinition.client = client;
    tuningJobDefinition.tuningAlgorithm = tuningInput.getTuningAlgorithm();
    tuningJobDefinition.tuningEnabled = 1;
    tuningJobDefinition.allowedMaxExecutionTimePercent = tuningInput.getAllowedMaxExecutionTimePercent();
    tuningJobDefinition.allowedMaxResourceUsagePercent = tuningInput.getAllowedMaxResourceUsagePercent();
    tuningJobDefinition.save();

    JobSuggestedParamSet jobSuggestedParamSet =
        insertDefaultJobExecution(job, flowExecId, jobExecId, flowExecUrl, jobExecUrl, flowDefinition,
            tuningInput.getTuningAlgorithm());
    insertDefaultParameters(jobSuggestedParamSet, defaultParams);

    logger.info("Added job: " + tuningInput.getJobDefId() + " for tuning");
    return tuningJobDefinition;
  }

  /**
   * Inserts default job execution in database
   * @param job Job
   * @param flowExecId Flow execution id
   * @param jobExecId Job execution id
   * @param flowExecUrl Flow execution url
   * @param jobExecUrl Job execution url
   * @return default job execution
   */
  private JobSuggestedParamSet insertDefaultJobExecution(JobDefinition job, String flowExecId, String jobExecId,
      String flowExecUrl, String jobExecUrl, FlowDefinition flowDefinition, TuningAlgorithm tuningAlgorithm) {
    logger.debug("Starting insertDefaultJobExecution");

    FlowExecution flowExecution =
        FlowExecution.find.where().eq(FlowExecution.TABLE.flowExecId, flowExecId).findUnique();

    if (flowExecution == null) {
      flowExecution = new FlowExecution();
      flowExecution.flowExecId = flowExecId;
      flowExecution.flowExecUrl = flowExecUrl;
      flowExecution.flowDefinition = flowDefinition;
      flowExecution.save();
    }

    JobExecution jobExecution = JobExecution.find.where().eq(JobExecution.TABLE.jobExecId, jobExecId).findUnique();

    if (jobExecution == null) {
      jobExecution = new JobExecution();
      jobExecution.job = job;
      jobExecution.executionState = ExecutionState.NOT_STARTED;
      jobExecution.jobExecId = jobExecId;
      jobExecution.jobExecUrl = jobExecUrl;
      jobExecution.flowExecution = flowExecution;
      jobExecution.save();
    }

    JobSuggestedParamSet jobSuggestedParamSet = new JobSuggestedParamSet();
    jobSuggestedParamSet.jobExecution = jobExecution;
    jobSuggestedParamSet.tuningAlgorithm = tuningAlgorithm;
    jobSuggestedParamSet.paramSetState = ParamSetStatus.CREATED;
    jobSuggestedParamSet.isParamSetDefault = true;
    jobSuggestedParamSet.save();

    logger.debug("Finishing insertDefaultJobExecution. Job Execution ID " + jobExecution.jobExecId);

    return jobSuggestedParamSet;
  }

  /**
   * Inserts default execution parameters in database
   * @param jobSuggestedParamSet Tuning Job Execution
   * @param defaultParams Default parameters map as string
   */
  @SuppressWarnings("unchecked")
  private void insertDefaultParameters(JobSuggestedParamSet jobSuggestedParamSet, String defaultParams) {
    JobExecution jobExecution = jobSuggestedParamSet.jobExecution;
    TuningAlgorithm.JobType jobType = jobSuggestedParamSet.tuningAlgorithm.jobType;

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Double> paramValueMap = null;
    try {
      paramValueMap = (Map<String, Double>) mapper.readValue(defaultParams, Map.class);
    } catch (Exception e) {
      logger.error(e);
    }
    if (paramValueMap != null) {
      for (Map.Entry<String, Double> paramValue : paramValueMap.entrySet()) {
        insertExecutionParameter(jobExecution, paramValue.getKey(), paramValue.getValue());
      }
    } else {
      logger.warn("ParamValueMap is null ");
    }
  }

  /**
   * Inserts parameter of an execution in database
   * @param jobExecution Job execution
   * @param paramName Parameter name
   * @param paramValue Parameter value
   */
  private void insertExecutionParameter(JobExecution jobExecution, String paramName, Double paramValue) {
    logger.debug("Starting insertExecutionParameter");
    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();
    jobSuggestedParamValue.jobExecution = jobExecution;
    TuningParameter tuningParameter =
        TuningParameter.find.where().eq(TuningParameter.TABLE.paramName, paramName).findUnique();
    if (tuningParameter != null) {
      jobSuggestedParamValue.tuningParameter = tuningParameter;
      jobSuggestedParamValue.paramValue = paramValue;
      jobSuggestedParamValue.save();
      logger.debug(
          "Finishing insertDefaultJobExecution. Job Execution ID. Param ID " + jobSuggestedParamValue.tuningParameter.id
              + " Param Name: " + jobSuggestedParamValue.tuningParameter.paramName);
    } else {
      logger.warn("TuningAlgorithm param null " + paramName);
    }
  }
}
