package com.linkedin.drelephant.tunin;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.JobExecution.ParamSetStatus;
import org.apache.log4j.*;
import play.libs.Json;

public class JobCompleteDetector {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  private AzkabanJobStatusUtil azkabanJobStatusUtil;

  public enum AzkabanJobStatus {
    FAILED,
    CANCELLED,
    KILLED,
    SUCCEEDED
  }

  public List<JobExecution> updateCompletedJobs() throws MalformedURLException, URISyntaxException {
    logger.error("100 Inside completed jobs");
    List<JobExecution> sentJobs = getJobExecution();
    logger.info("Sent jobs:"+ Json.toJson(sentJobs));
    List<JobExecution> completedJobs = getCompletedJob(sentJobs);
    updateJobStatus(completedJobs);
    logger.error("Finished completed jobs");
    return completedJobs;
  }

  public List<JobExecution> getJobExecution() {
    logger.error("100 Inside getJobExecution jobs");
    List<JobExecution> jobExecutions =
        JobExecution.find.select("*").where().eq(JobExecution.TABLE.paramSetState, ParamSetStatus.SENT).findList();
    logger.error("Finished getJobExecution jobs");
    return jobExecutions;
  }

  public List<JobExecution> getCompletedJob(List<JobExecution> jobExecutions) throws MalformedURLException,
      URISyntaxException {

    logger.error("Inside getCompletedJob jobs:\n" + Json.toJson(jobExecutions));
    List<JobExecution> completedJobs = new ArrayList<JobExecution>();
    try
    {
    for (JobExecution jobExecution : jobExecutions) {

      logger.info("FLow Execution Id: " + jobExecution.flowExecId);

      if (azkabanJobStatusUtil == null) {
        logger.error("Initializing  AzkabanJobStatusUtil");
        azkabanJobStatusUtil = new AzkabanJobStatusUtil(jobExecution.flowExecId);
      }

      logger.error("Calling  getJobsFromFlow");

      try {
        Map<String, String> jobStatus = azkabanJobStatusUtil.getJobsFromFlow(jobExecution.flowExecId);
        if (jobStatus != null) {
          for (Map.Entry<String, String> job : jobStatus.entrySet()) {
            logger.error("Job Found:" + job.getKey() + ". Status: " + job.getValue());
            if (job.getKey().equals(jobExecution.job.jobName)) {
              if (job.getValue().equals(AzkabanJobStatus.FAILED.toString())) {
                jobExecution.paramSetState = ParamSetStatus.EXECUTED;
                jobExecution.executionState = ExecutionState.FAILED;
              }
              if (job.getValue().equals(AzkabanJobStatus.CANCELLED.toString()) || job.getValue().equals(AzkabanJobStatus.KILLED.toString())) {
                jobExecution.paramSetState = ParamSetStatus.EXECUTED;
                jobExecution.executionState = ExecutionState.CANCELLED;
              }
              if (job.getValue().equals(AzkabanJobStatus.SUCCEEDED.toString())) {
                jobExecution.paramSetState = ParamSetStatus.EXECUTED;
                jobExecution.executionState = ExecutionState.SUCCEDED;
              }
              if (jobExecution.paramSetState.equals(ParamSetStatus.EXECUTED)) {
                completedJobs.add(jobExecution);
              }
            }
          }
        } else {
          logger.error("No jobs found for flow execution: " + jobExecution.jobExecId);
        }
      } catch(Exception e){
        logger.error("Error: ", e);
        logger.error("Stack trace: " + e.getStackTrace());
      }
    }
    }catch(Exception e)
    {
      e.printStackTrace();
      logger.error("Error in log " , e);
      logger.error("ERROR IN " + e.getStackTrace());
    }
    logger.error("Finished getCompletedJob jobs");
    logger.info("Completed jobs: " + completedJobs);
    return completedJobs;
  }

  public boolean updateJobStatus(List<JobExecution> jobExecutions) {
    boolean updateStatus = true;
    for (JobExecution jobExecution : jobExecutions) {
      logger.error("Updating jobExecution: " + jobExecution.jobExecId);
      jobExecution.update();
    }
    return updateStatus;
  }
}
