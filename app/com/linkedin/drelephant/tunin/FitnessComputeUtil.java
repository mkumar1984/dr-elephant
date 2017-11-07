package com.linkedin.drelephant.tunin;

import java.util.List;

import models.AppHeuristicResult;
import models.AppHeuristicResultDetails;
import models.AppResult;
import models.JobExecution;
import models.JobExecution.ParamSetStatus;

import org.apache.log4j.Logger;


public class FitnessComputeUtil {
  private static final Logger logger = Logger.getLogger(JobCompleteDetector.class);

  public List<JobExecution> getJobExecution() {
    logger.error("100 Inside getJobExecution jobs");
    List<JobExecution> jobExecutions =
        JobExecution.find.select("*").where().eq(JobExecution.TABLE.paramSetState, ParamSetStatus.EXECUTED).findList();
    logger.error("Finished getJobExecution jobs");
    return jobExecutions;
  }

  public void updateJobMetrics(List<JobExecution> executedJobs) {
    logger.error("Inside updateJobMetrics");
    for (JobExecution jobExecution : executedJobs) {
      logger.error("Job Execution Update: Flow Execution ID " + jobExecution.flowExecutionId + " Job ID " + jobExecution.jobExecutionId);
      List<AppResult> results =
          AppResult.find
              .select("*")
              .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
              .fetch(
                  AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
                  "*")
               .where()
               .eq(AppResult.TABLE.FLOW_EXEC_ID, jobExecution.flowExecutionId)
              .eq(AppResult.TABLE.JOB_DEF_ID, jobExecution.job.jobDefId).findList();
      if (results != null) {
        Long totalExecutionTime = 0L;
        Double totalResourceUsed = 0D;
        Double totalInputBytesInMB = 0D;
        if (results != null) {
          for (AppResult appResult : results) {
            logger.error("Job Execution Update: ApplicationID " + appResult.id);
            Long executionTime = appResult.finishTime - appResult.startTime - appResult.totalDelay;
            totalExecutionTime += executionTime;
            totalResourceUsed += appResult.resourceUsed;
            totalInputBytesInMB += getTotalInputBytes(appResult);
          }
        }
        if (totalExecutionTime != 0) {
          jobExecution.executionTime = totalExecutionTime * 1.0 / (1000 * 60);
          jobExecution.resourceUsage = totalResourceUsed * 1.0 / (1024 * 3600);
          jobExecution.inputSizeInMb = totalInputBytesInMB;
          logger.error("Job Execution Update: UpdatedValue " + totalExecutionTime +":" + totalResourceUsed + ":" + totalInputBytesInMB);
          jobExecution.update();
        }
      }
    }
    logger.error("Finished updateJobMetrics");
  }

  public Long getTotalInputBytes(AppResult appResult) {
    Long totalInputBytes = 0L;
    if (appResult.yarnAppHeuristicResults != null) {
      for (AppHeuristicResult appHeuristicResult : appResult.yarnAppHeuristicResults) {
        if (appHeuristicResult.heuristicName.equals("Mapper Speed")) {
          if (appHeuristicResult.yarnAppHeuristicResultDetails != null) {
            for (AppHeuristicResultDetails appHeuristicResultDetails : appHeuristicResult.yarnAppHeuristicResultDetails) {
              if (appHeuristicResultDetails.name.equals("Total input size in MB")) {
                totalInputBytes += Long.parseLong(appHeuristicResultDetails.value);
              }
            }
          }
        }
      }
    }
    return totalInputBytes;
  }
}
