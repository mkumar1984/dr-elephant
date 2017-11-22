package com.linkedin.drelephant.tunin;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Algo;
import models.AlgoParam;
import models.Job;
import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.JobExecution.ParamSetStatus;
import models.JobSuggestedParamValue;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AutoTuningAPIHelper {

  private static final Logger logger = Logger.getLogger(AutoTuningAPIHelper.class);
  Algo algo = Algo.find.where().idEq(1).findUnique();

  public Map<String, Double> getCurrentRunParameters(String flowDefId, String jobDefId, String flowDefUrl,
                                                     String  jobDefUrl, String flowExecId, String jobExecId,
                                                     String flowExecUrl, String jobExecUrl, String jobName,
                                                     String userName, String client, String scheduler,
                                                     String defaultParams, Boolean isRetry,
                                                     Boolean skipExecutionForOptimization) {

    logger.error("Starting getCurrentRunParameters");
    Job job =
        Job.find.select("*").where().eq(Job.TABLE.jobDefId, jobDefId)
            .eq(Job.TABLE.flowDefId, flowDefId).findUnique();

    if (job == null) {
      logger.error("Job Not found. Creating new job. ");
      job =
          addNewJobForTuning(flowDefId, jobDefId, flowDefUrl, jobDefUrl, flowExecId, jobExecId, flowExecUrl, jobExecUrl,
                  jobName, userName,  client, scheduler, defaultParams, isRetry, skipExecutionForOptimization);
    }

    logger.error("Finding execution for job ID " + job.jobId);

    JobExecution jobExecution =
        JobExecution.find.select("*").fetch(JobExecution.TABLE.job, "*").where()
            .eq(JobExecution.TABLE.job + "." + JobExecution.TABLE.jobId, job.jobId).eq(JobExecution.TABLE.paramSetState, ParamSetStatus.CREATED)
            .order().asc(JobExecution.TABLE.createdTs).setMaxRows(1).findUnique();


    if (jobExecution == null) {
      jobExecution =
          JobExecution.find.select("*").where().eq(JobExecution.TABLE.job + "." + JobExecution.TABLE.jobId, job.jobId)
              .eq(JobExecution.TABLE.isDefaultExecution, true).setMaxRows(1).findUnique();
    }

    logger.error("Finding parameters for param set ID " + jobExecution.paramSetId);

    List<JobSuggestedParamValue> jobSuggestedParamValues =
        JobSuggestedParamValue.find.where().eq(JobSuggestedParamValue.TABLE.paramSetId, jobExecution.paramSetId).findList();

    logger.error("Number of output parameters : " + jobSuggestedParamValues.size());
    Map<String, Double> paramValues = new HashMap<String, Double>();
    if (jobSuggestedParamValues != null) {
      for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValues) {
        logger.error("Param Name is " + jobSuggestedParamValue.algoParam.paramName + " And value is " +
                jobSuggestedParamValue.paramValue);
        paramValues.put(jobSuggestedParamValue.algoParam.paramName, jobSuggestedParamValue.paramValue);
      }
    }

    updateJobExecutionParameter(jobExecution, flowExecId, jobExecId);

    logger.error("Finishing getCurrentRunParameters");
    return paramValues;
  }

  public void updateJobExecutionParameter(JobExecution jobExecution, String flowExecId, String jobExecId) {
    jobExecution.flowExecId = flowExecId;
    jobExecution.jobExecId = jobExecId;
    jobExecution.paramSetState = ParamSetStatus.SENT;
    jobExecution.executionState = ExecutionState.IN_PROGRESS;
    jobExecution.update();
  }

  public Job addNewJobForTuning(String flowDefId, String jobDefId, String flowDefUrl,
                                String  jobDefUrl, String flowExecId, String jobExecId,
                                String flowExecUrl, String jobExecUrl, String jobName,
                                String userName, String client, String scheduler,
                                String defaultParams, Boolean isRetry,
                                Boolean skipExecutionForOptimization) {

    logger.error("Starting addNewJobForTuning");
    Job job = insertJob(flowDefId, jobDefId, flowDefUrl, jobDefUrl, jobName, userName, client, scheduler);
    JobExecution jobExecution = insertDefaultJobExecution(job, flowExecId, jobExecId, flowExecUrl, jobExecUrl);
    insertDefaultParameters(jobExecution, defaultParams);
    logger.error("Finishing addNewJobForTuning");
    return job;
  }

  public Job insertJob(String flowDefId, String jobDefId, String flowDefUrl, String jobDefUrl, String jobName,
                       String userName, String client, String scheduler) {
    logger.error("Starting insertJob");
    Job job = new Job();
    job.algo = algo;
    job.flowDefId = flowDefId;
    job.jobDefId = jobDefId;
    job.flowDefUrl = flowDefUrl;
    job.jobDefUrl = jobDefUrl;
    job.jobName = jobName;
    job.username = userName;
    job.client = client;
    job.scheduler = scheduler;
    job.tuningEnabled = true;
    job.deleted = false;
    job.save();
    logger.error("Finishing insertJob. JobID is " + job.jobId);
    return job;
  }

  public JobExecution insertDefaultJobExecution(Job job, String flowExecId, String jobExecId, String flowExecUrl,
                                                String jobExecUrl) {
    logger.error("Starting insertDefaultJobExecution");
    JobExecution jobExecution = new JobExecution();
    jobExecution.job = job;
    jobExecution.algo = algo;
    jobExecution.paramSetState = ParamSetStatus.CREATED;
    jobExecution.executionState = ExecutionState.NOT_STARTED;
    jobExecution.isDefaultExecution = true;
    jobExecution.flowExecId = flowExecId;
    jobExecution.jobExecId = jobExecId;
    jobExecution.flowExecUrl = flowExecUrl;
    jobExecution.jobExecUrl = jobExecUrl;
    jobExecution.save();
    logger.error("Finishing insertDefaultJobExecution. Job Execution ID " + jobExecution.jobExecId);
    return jobExecution;
  }

  public void insertDefaultParameters(JobExecution jobExecution, String defaultParams) {
    @SuppressWarnings("unchecked")
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Double> paramValueMap = null;
    try {
      paramValueMap = (Map<String, Double>) mapper.readValue(defaultParams, Map.class);
    } catch (JsonParseException e) {
      logger.error("Error is " + e);
    } catch (JsonMappingException e) {
      logger.error("Error is " + e);
    } catch (IOException e) {
      logger.error("Error is " + e);
    }
    if (paramValueMap != null) {
      for (Map.Entry<String, Double> paramValue : paramValueMap.entrySet()) {
        insertJobParameter(jobExecution, paramValue.getKey(), paramValue.getValue());
      }
    }else
    {
      logger.error("ParamValueMap is null " );
    }
  }

  public void insertJobParameter(JobExecution jobExecution, String paramName, Double paramValue) {
    logger.error("Starting insertJobParameter");
    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();
    jobSuggestedParamValue.jobExecution = jobExecution;
    AlgoParam algoParam = AlgoParam.find.where().eq(AlgoParam.TABLE.paramName, paramName).findUnique();
    if (algoParam != null) {
      jobSuggestedParamValue.algoParam = algoParam;
      jobSuggestedParamValue.paramValue = paramValue;
      jobSuggestedParamValue.save();
      logger.error("Finishing insertDefaultJobExecution. Job Execution ID. Param ID "
          + jobSuggestedParamValue.algoParam.paramId + " Param Name: " + jobSuggestedParamValue.algoParam.paramName);
    }else
    {
      logger.error("Algo param null " + paramName);
    }
  }

}
