package com.linkedin.drelephant.tunin;

import java.util.List;

import models.AppResult;
import models.TuningJobDefinition;

import org.apache.log4j.Logger;

import play.libs.Json;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlRow;


public class BaselineComputeUtil {

  public static Integer numJobsForBaseline = 30;
  private final Logger logger = Logger.getLogger(getClass());

  public List<TuningJobDefinition> computeBaseline() {
    try {
      List<TuningJobDefinition> tuningJobDefinitions = getJobForBaselineComputation();
      for (TuningJobDefinition tuningJobDefinition : tuningJobDefinitions) {
        updateBaselineForJob(tuningJobDefinition);
      }
      return tuningJobDefinitions;
    } catch (Exception e) {
      logger.error("Error in computing baseline for ", e);
    }
    return null;
  }

  public List<TuningJobDefinition> getJobForBaselineComputation() {
    logger.error("Starting Computing baseline for jobs: ");

    List<TuningJobDefinition> tuningJobDefinitions =
        TuningJobDefinition.find.where().eq(TuningJobDefinition.TABLE.averageResourceUsage, null).findList();
    logger.error("Computing baseline for jobs: " + Json.toJson(tuningJobDefinitions));
    return tuningJobDefinitions;
  }

  public void updateBaselineForJob(TuningJobDefinition tuningJobDefinition) {


    logger.error("Computing baseline for jobs: " + Json.toJson(tuningJobDefinition));

    String sql="SELECT AVG(resource_used) AS resource_used, AVG(execution_time) AS execution_time FROM "
        + "(SELECT job_exec_id, SUM(resource_used) AS resource_used, "
        + "SUM(finish_time - start_time - total_delay) AS execution_time, "
        + "MAX(start_time) AS start_time "
        + "FROM yarn_app_result WHERE job_def_id=:jobDefId "
        + "GROUP BY job_exec_id "
        + "ORDER BY start_time DESC "
        + "LIMIT :num) temp";

    SqlRow baseline=Ebean.createSqlQuery(sql)
      .setParameter("jobDefId", tuningJobDefinition.job.jobDefId)
      .setParameter("num", numJobsForBaseline)
      .findUnique();

    Double avgResourceUsage = 0D;
    Double avgExecutionTime = 0D;
    avgResourceUsage = baseline.getDouble("resource_used") / (1024 * 3600);
    avgExecutionTime = baseline.getDouble("execution_time") / (1000 * 60);
    tuningJobDefinition.averageExecutionTime = avgExecutionTime;
    tuningJobDefinition.averageResourceUsage = avgResourceUsage;
    tuningJobDefinition.averageInputSizeInBytes=getAvgInputSizeInBytes(tuningJobDefinition.job.jobDefId);
    logger.error("Resource usage " + avgResourceUsage + " Execution Time " + avgExecutionTime);
    tuningJobDefinition.update();
  }

  public Long getAvgInputSizeInBytes(String jobDefId)
  {
    String sql="SELECT AVG(inputSizeInBytes) as avgInputSizeInMB FROM "
        + "(SELECT job_exec_id, SUM(value) inputSizeInBytes, MAX(start_time) AS start_time "
        + "FROM yarn_app_result yar INNER JOIN yarn_app_heuristic_result yahr "
        + "ON yar.id=yahr.yarn_app_result_id "
        + "INNER JOIN yarn_app_heuristic_result_details yahrd "
        + "ON yahr.id=yahrd.yarn_app_heuristic_result_id "
        + "WHERE job_def_id=:jobDefId AND yahr.heuristic_name='Mapper Speed' "
        + "AND yahrd.name='Total input size in MB' "
        + "GROUP BY job_exec_id ORDER BY start_time DESC LIMIT :num ) temp";

    SqlRow baseline=Ebean.createSqlQuery(sql)
        .setParameter("jobDefId", jobDefId)
        .setParameter("num", numJobsForBaseline)
        .findUnique();
    Double avgInputSizeInBytes=baseline.getDouble("avgInputSizeInMB") * 1024 * 1024;
    return avgInputSizeInBytes.longValue();
  }
}
