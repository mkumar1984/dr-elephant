package com.linkedin.drelephant.tuning.Schduler;

import com.linkedin.drelephant.clients.azkaban.AzkabanJobStatusUtil;
import com.linkedin.drelephant.tuning.AbstractJobStatusManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.JobExecution;
import models.JobSuggestedParamSet;
import models.TuningJobExecutionParamSet;

import org.apache.log4j.Logger;


public class AzkabanJobStatusManager extends AbstractJobStatusManager {

  private final Logger logger = Logger.getLogger(getClass());
  private AzkabanJobStatusUtil _azkabanJobStatusUtil;
  private static Pattern jobNamePattern=Pattern.compile("job=([^&]*)");


  public enum AzkabanJobStatus {
    FAILED, CANCELLED, KILLED, SUCCEEDED, SKIPPED
  }

  protected List<TuningJobExecutionParamSet> detectJobsExecutionInProgress() {
    logger.info("Fetching the executions which are in progress");
    List<TuningJobExecutionParamSet> tuningJobExecutionParamSets =
        TuningJobExecutionParamSet.find.
            fetch(TuningJobExecutionParamSet.TABLE.jobExecution)
            .fetch(TuningJobExecutionParamSet.TABLE.jobSuggestedParamSet)
              .where()
            .eq(TuningJobExecutionParamSet.TABLE.jobExecution + '.' + JobExecution.TABLE.executionState,
                JobExecution.ExecutionState.IN_PROGRESS)
            .findList();


    logger.info("Number of executions which are in progress: " + tuningJobExecutionParamSets.size());
    return tuningJobExecutionParamSets;
  }

  @Override
  protected boolean analyzeCompletedJobsExecution(List<TuningJobExecutionParamSet> inProgressExecutionParamSet) {
    logger.info("Fetching the list of executions completed since last iteration");
    List<JobExecution> completedExecutions = new ArrayList<JobExecution>();
    boolean isAnalyzeDone = true;
    try {
      for (TuningJobExecutionParamSet tuningJobExecutionParamSet : inProgressExecutionParamSet) {
        JobSuggestedParamSet jobSuggestedParamSet = tuningJobExecutionParamSet.jobSuggestedParamSet;
        JobExecution jobExecution = tuningJobExecutionParamSet.jobExecution;
        logger.info("Checking current status of started execution: " + jobExecution.jobExecId);
        assignAzkabanJobStatusUtil();
        isAnalyzeDone = analyzeJobExecution(jobExecution,jobSuggestedParamSet);
        if(isAnalyzeDone) {
          completedExecutions.add(jobExecution);
        }
      }
    } catch (Exception e) {
      logger.error("Error in fetching list of completed executions", e);
      return false;
    }
    logger.info("Number of executions completed since last iteration: " + completedExecutions.size());
    return true;
  }

  private void assignAzkabanJobStatusUtil() {
    if (_azkabanJobStatusUtil == null) {
      logger.info("Initializing  AzkabanJobStatusUtil");
      _azkabanJobStatusUtil = new AzkabanJobStatusUtil();
    }
  }

  private boolean analyzeJobExecution(JobExecution jobExecution,JobSuggestedParamSet jobSuggestedParamSet){
    try {
      logger.debug(" Getting jobs for Flow "+jobExecution.flowExecution.flowExecId);
      String jobName="";
      Matcher m=jobNamePattern.matcher(jobExecution.jobExecId);
      if(m.find()){
        jobName = m.group(1);
        logger.info("Expression matched. Job Name is " + jobName);
      }else{
        logger.error("Job expression not matched for job " + jobExecution.jobExecId);
      }
      Map<String, String> jobStatus = _azkabanJobStatusUtil.getJobsFromFlow(jobExecution.flowExecution.flowExecId);
      if (jobStatus != null) {
        for (Map.Entry<String, String> job : jobStatus.entrySet()) {
          if (job.getKey().equals(jobName)) {
            logger.info("Job Found:" + job.getKey() + ". Status: " + job.getValue());
            updateJobExecutionMetrics(job, jobSuggestedParamSet, jobExecution);
          }
        }
      } else {
        logger.debug("No jobs found for flow execution: " + jobExecution.flowExecution.flowExecId);
        return false;
      }
    } catch (Exception e) {
      logger.error("Error in checking status of execution: " + jobExecution.jobExecId, e);
      return false;
    }
    return true;
  }

  private void updateJobExecutionMetrics(Map.Entry<String, String> job, JobSuggestedParamSet jobSuggestedParamSet,
      JobExecution jobExecution) {
    if (job.getValue().equals(AzkabanJobStatus.FAILED.toString())) {
      if (jobSuggestedParamSet.paramSetState.equals(JobSuggestedParamSet.ParamSetStatus.SENT)) {
        jobSuggestedParamSet.paramSetState = JobSuggestedParamSet.ParamSetStatus.EXECUTED;
      }
      jobExecution.executionState = JobExecution.ExecutionState.FAILED;
    } else if (job.getValue().equals(AzkabanJobStatus.SUCCEEDED.toString())) {
      if (jobSuggestedParamSet.paramSetState.equals(JobSuggestedParamSet.ParamSetStatus.SENT)) {
        jobSuggestedParamSet.paramSetState = JobSuggestedParamSet.ParamSetStatus.EXECUTED;
      }
      jobExecution.executionState = JobExecution.ExecutionState.SUCCEEDED;
    } else if (job.getValue().equals(AzkabanJobStatus.CANCELLED.toString()) || job.getValue()
        .equals(AzkabanJobStatus.KILLED.toString()) || job.getValue()
        .equals(AzkabanJobStatus.SKIPPED.toString())) {
      if (jobSuggestedParamSet.paramSetState.equals(JobSuggestedParamSet.ParamSetStatus.SENT)) {
        jobSuggestedParamSet.paramSetState = JobSuggestedParamSet.ParamSetStatus.EXECUTED;
      }
      jobExecution.executionState = JobExecution.ExecutionState.CANCELLED;
    }
  }

  @Override
  public String getManagerName() {
    return "AzkabanJobStatusManager";
  }

}
