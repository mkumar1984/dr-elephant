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

public class JobCompleteDetector {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  private AzkabanJobStatusUtil azkabanJobStatusUtil;

  public enum JobStatus {
    FAILED,
    CANCELLED,
    KILLED,
    SUCCEEDED
  }

  public List<JobExecution> updateCompletedJobs(String token) throws MalformedURLException, URISyntaxException {
    logger.error("100 Inside completed jobs");
    List<JobExecution> sentJobs = getJobExecution();
    List<JobExecution> completedJobs = getCompletedJob(sentJobs, token);
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

  public List<JobExecution> getCompletedJob(List<JobExecution> jobExecutions, String token) throws MalformedURLException,
      URISyntaxException {

    logger.error("100 Inside getCompletedJob jobs");
    List<JobExecution> completedJobs = new ArrayList<>();
    try
    {
    for (JobExecution jobExecution : jobExecutions) {
      if (azkabanJobStatusUtil == null) {
        logger.error("Initializing  AzkabanJobStatusUtil");
        azkabanJobStatusUtil = new AzkabanJobStatusUtil(jobExecution.flowExecutionId, token);
      }
      logger.error("Calling  getJobsFromFlow");
      Map<String, String> jobStatus = azkabanJobStatusUtil.getJobsFromFlow(jobExecution.flowExecutionId);
      for (Map.Entry<String, String> job : jobStatus.entrySet()) {
        logger.error("Job Found + " + job.getKey() + ". Status: " + job.getValue());
        if (job.getKey().equals(jobExecution.jobExecutionId)) {
          if (job.getValue().equals(JobStatus.FAILED.toString())) {
            jobExecution.paramSetState = ParamSetStatus.EXECUTED;
            jobExecution.executionState = ExecutionState.FAILED;
          }
          if (job.getValue().equals(JobStatus.CANCELLED.toString()) || job.getValue().equals(JobStatus.KILLED.toString())) {
            jobExecution.paramSetState = ParamSetStatus.EXECUTED;
            jobExecution.executionState = ExecutionState.CANCELLED;
          }
          if (job.getValue().equals(JobStatus.SUCCEEDED.toString())) {
            jobExecution.paramSetState = ParamSetStatus.EXECUTED;
            jobExecution.executionState = ExecutionState.SUCCEDED;
          }
          if (jobExecution.paramSetState.equals(ParamSetStatus.EXECUTED)) {
            completedJobs.add(jobExecution);
          }
        }
      }
    }
    }catch(Exception e)
    {
      e.printStackTrace();
      logger.error("Error in log " , e);
      logger.error("ERROR IN " + e.getStackTrace().toString());
    }
    logger.error("Finished getCompletedJob jobs");

    return completedJobs;
  }

  public boolean updateJobStatus(List<JobExecution> jobExecutions) {
    boolean updateStatus = true;
    for (JobExecution jobExecution : jobExecutions) {
      logger.error("Updating jobExecution + " + jobExecution.flowExecutionId);
      jobExecution.update();
    }
    return updateStatus;
  }
}
