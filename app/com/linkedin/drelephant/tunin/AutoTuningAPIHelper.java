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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.FlowDefinition;
import models.FlowExecution;
import models.TuningAlgorithm;
import models.TuningJobDefinition;
import models.TuningJobExecution;
import models.TuningParameter;
import models.Job;
import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.TuningJobExecution.ParamSetStatus;
import models.JobSuggestedParamValue;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.util.Utils;

import play.libs.Json;


/**
 * This class processes the API requests and returns param suggestion as response
 */
public class AutoTuningAPIHelper {

  private static final Logger logger = Logger.getLogger(AutoTuningAPIHelper.class);

  public static final String ALLOWED_MAX_RESOURCE_USAGE_PERCENT_DEFAULT =
      "autotuning.default.allowed_max_resource_usage_percent";

  public static final String ALLOWED_MAX_EXECUTION_TIME_PERCENT_DEFAULT =
      "autotuning.default.allowed_max_execution_time_percent";

  //Todo: this will not be hard coded
  //TuningAlgorithm _tuningAlgorithm = TuningAlgorithm.find.where ().idEq (1).findUnique ();

  public TuningJobExecution createDefaultJobExecution(TuningJobDefinition tuningJobDefinition) {
    TuningJobExecution tuningJobExecutionDefault =
        TuningJobExecution.find
            .select("*")
            .where()
            .eq(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + Job.TABLE.id,
                tuningJobDefinition.job.id).eq(TuningJobExecution.TABLE.isDefaultExecution, true).setMaxRows(1)
            .findUnique();

    TuningJobExecution tuningJobExecution = new TuningJobExecution();
    JobExecution jobExecution = new JobExecution();
    jobExecution.id = 0L;
    jobExecution.job = tuningJobExecutionDefault.jobExecution.job;
    jobExecution.executionState = ExecutionState.NOT_STARTED;
    jobExecution.save();
    tuningJobExecution.jobExecution = jobExecution;
    tuningJobExecution.isDefaultExecution = tuningJobExecutionDefault.isDefaultExecution;
    tuningJobExecution.tuningAlgorithm = tuningJobExecutionDefault.tuningAlgorithm;
    tuningJobExecution.paramSetState = ParamSetStatus.CREATED;
    tuningJobExecution.save();

    logger.debug("New Default tuning execution: " + Json.toJson(tuningJobExecution));

    List<JobSuggestedParamValue> jobSuggestedParamValueList =
        JobSuggestedParamValue.find
            .where()
            .eq(JobSuggestedParamValue.TABLE.jobExecution + "." + JobExecution.TABLE.id,
                tuningJobExecutionDefault.jobExecution.id).findList();

    for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValueList) {
      JobSuggestedParamValue jobSuggestedParamValue1 = new JobSuggestedParamValue();
      jobSuggestedParamValue1.id = 0;
      jobSuggestedParamValue1.jobExecution = jobExecution;
      jobSuggestedParamValue1.paramValue = jobSuggestedParamValue.paramValue;
      jobSuggestedParamValue1.tuningParameter = jobSuggestedParamValue.tuningParameter;
      jobSuggestedParamValue1.save();
    }

    tuningJobExecution =
        TuningJobExecution.find
            .select("*")
            .where()
            .eq(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.id, tuningJobExecution.jobExecution.id)
            .setMaxRows(1).findUnique();

    return tuningJobExecution;
  }

  public void setDefaultValue(TuningInput tuningInput) {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    if (tuningInput.getAllowedMaxExecutionTimePercent() == null) {
      Double allowedMaxExecutionTimePercent = new Double(Utils.getNonNegativeInt(configuration, "", 150));
      tuningInput.setAllowedMaxExecutionTimePercent(allowedMaxExecutionTimePercent);
    }
    if (tuningInput.getAllowedMaxResourceUsagePercent() == null) {
      Double allowedMaxResourceUsagePercent = new Double(Utils.getNonNegativeInt(configuration, "", 150));
      tuningInput.setAllowedMaxResourceUsagePercent(allowedMaxResourceUsagePercent);
    }

    TuningAlgorithm tuningAlgorithm =
        TuningAlgorithm.find.where().eq("job_type", tuningInput.getJobType())
            .eq("optimization_metric", tuningInput.getOptimizationMetric()).findUnique();
    tuningInput.setTuningAlgorithm(tuningAlgorithm);
  }

  /**
   * Handles the api request and returns param suggestions as response
   * @param tuningInput Rest api parameters
   * @return Parameter Suggestion
   */
  public Map<String, Double> getCurrentRunParameters(TuningInput tuningInput) {

    String jobDefId = tuningInput.getJobDefId();

    logger.debug("Processing request getCurrentRunParameters");

    TuningJobDefinition tuningJobDefinition =
        TuningJobDefinition.find.select("*").fetch(TuningJobDefinition.TABLE.job, "*").where()
            .eq(TuningJobDefinition.TABLE.job + "." + Job.TABLE.jobDefId, jobDefId)
            .eq(TuningJobDefinition.TABLE.tuningEnabled, 1).findUnique();

    if (tuningJobDefinition == null) {
      logger.debug("New job encountered, creating new entry. ");
      tuningJobDefinition = addNewJobForTuning(tuningInput);
    }

    logger.debug("Finding execution for job ID " + tuningJobDefinition.job.id);
    TuningJobExecution tuningJobExecution =
        TuningJobExecution.find
            .select("*")
            .fetch(TuningJobExecution.TABLE.jobExecution, "*")
            .fetch(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job, "*")
            .where()
            .eq(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + Job.TABLE.id,
                tuningJobDefinition.job.id).eq(TuningJobExecution.TABLE.paramSetState, ParamSetStatus.CREATED).order()
            .asc(TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.createdTs).setMaxRows(1).findUnique();

    if (tuningJobExecution == null) {
      tuningJobExecution = createDefaultJobExecution(tuningJobDefinition);
    }

    logger.debug("Finding parameters for param set ID " + tuningJobExecution.jobExecution.id);
    List<JobSuggestedParamValue> jobSuggestedParamValues =
        JobSuggestedParamValue.find
            .where()
            .eq(JobSuggestedParamValue.TABLE.jobExecution + "." + JobExecution.TABLE.id,
                tuningJobExecution.jobExecution.id).findList();

    logger.debug("Number of output parameters : " + jobSuggestedParamValues.size());
    Map<String, Double> paramValues = new HashMap<String, Double>();

    if (jobSuggestedParamValues != null) {
      for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValues) {
        logger.debug("Param Name is " + jobSuggestedParamValue.tuningParameter.paramName + " And value is "
            + jobSuggestedParamValue.paramValue);
        paramValues.put(jobSuggestedParamValue.tuningParameter.paramName, jobSuggestedParamValue.paramValue);
      }
    }

    updateJobExecutionParameter(tuningJobExecution, tuningInput);

    logger.info("Finishing getCurrentRunParameters");
    return paramValues;
  }

  /**
   *This is to update job execution
   * @param tuningJobExecution
   * @param tuningInput
   */
  public void updateJobExecutionParameter(TuningJobExecution tuningJobExecution, TuningInput tuningInput) {

    FlowExecution flowExecution =
        FlowExecution.find.where().eq(FlowExecution.TABLE.flowExecId, tuningInput.getFlowExecId()).findUnique();

    if (flowExecution == null) {
      flowExecution = new FlowExecution();
      flowExecution.flowExecId = tuningInput.getFlowExecId();
      flowExecution.flowExecUrl = tuningInput.getFlowExecUrl();
      flowExecution.flowDefinition = tuningJobExecution.jobExecution.job.flowDefinition;
      flowExecution.save();
    }

    JobExecution jobExecution = tuningJobExecution.jobExecution;
    jobExecution.jobExecId = tuningInput.getJobExecId();
    jobExecution.jobExecUrl = tuningInput.getJobExecUrl();
    jobExecution.executionState = ExecutionState.IN_PROGRESS;
    jobExecution.flowExecution = flowExecution;

    logger.debug("Saving job execution" + Json.toJson(jobExecution));

    jobExecution.save();

    tuningJobExecution.jobExecution = jobExecution;
    tuningJobExecution.paramSetState = ParamSetStatus.SENT;
    tuningJobExecution.save();
  }

  /**
   * Add new job for tuning
   * @param tuningInput Tuning input parameters
   * @return Job
   */
  public TuningJobDefinition addNewJobForTuning(TuningInput tuningInput) {

    logger.debug("Starting addNewJobForTuning");

    Job job = Job.find.select("*").where().eq(Job.TABLE.jobDefId, tuningInput.getJobDefId()).findUnique();

    FlowDefinition flowDefinition =
        FlowDefinition.find.where().eq(FlowDefinition.TABLE.flowDefId, tuningInput.getFlowDefId()).findUnique();

    if (flowDefinition == null) {
      flowDefinition = new FlowDefinition();
      flowDefinition.flowDefId = tuningInput.getFlowDefId();
      flowDefinition.flowDefUrl = tuningInput.getFlowDefUrl();
      flowDefinition.save();
    }

    if (job == null) {
      job = new Job();
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
    tuningJobDefinition.save();

    TuningJobExecution tuningJobExecution =
        insertDefaultJobExecution(job, flowExecId, jobExecId, flowExecUrl, jobExecUrl, flowDefinition,
            tuningInput.getTuningAlgorithm());
    insertDefaultParameters(tuningJobExecution.jobExecution, defaultParams);

    logger.debug("Finishing addNewJobForTuning");
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
  public TuningJobExecution insertDefaultJobExecution(Job job, String flowExecId, String jobExecId, String flowExecUrl,
      String jobExecUrl, FlowDefinition flowDefinition, TuningAlgorithm tuningAlgorithm) {
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

    TuningJobExecution tuningJobExecution = new TuningJobExecution();
    tuningJobExecution.jobExecution = jobExecution;
    tuningJobExecution.tuningAlgorithm = tuningAlgorithm;
    tuningJobExecution.paramSetState = ParamSetStatus.CREATED;
    tuningJobExecution.isDefaultExecution = true;
    tuningJobExecution.save();

    logger.debug("Finishing insertDefaultJobExecution. Job Execution ID " + jobExecution.jobExecId);

    return tuningJobExecution;
  }

  /**
   * Inserts default execution parameters in database
   * @param jobExecution Job Execution
   * @param defaultParams Default parameters map as string
   */
  @SuppressWarnings("unchecked")
  public void insertDefaultParameters(JobExecution jobExecution, String defaultParams) {
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
  public void insertExecutionParameter(JobExecution jobExecution, String paramName, Double paramValue) {
    logger.debug("Starting insertExecutionParameter");
    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();
    jobSuggestedParamValue.jobExecution = jobExecution;
    TuningParameter tuningParameter =
        TuningParameter.find.where().eq(TuningParameter.TABLE.paramName, paramName).findUnique();
    if (tuningParameter != null) {
      jobSuggestedParamValue.tuningParameter = tuningParameter;
      jobSuggestedParamValue.paramValue = paramValue;
      jobSuggestedParamValue.save();
      logger.debug("Finishing insertDefaultJobExecution. Job Execution ID. Param ID "
          + jobSuggestedParamValue.tuningParameter.id + " Param Name: "
          + jobSuggestedParamValue.tuningParameter.paramName);
    } else {
      logger.warn("TuningAlgorithm param null " + paramName);
    }
  }
}
