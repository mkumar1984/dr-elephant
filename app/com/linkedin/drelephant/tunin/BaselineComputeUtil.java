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

import java.util.List;

import models.TuningJobDefinition;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import play.libs.Json;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlRow;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.mapreduce.heuristics.CommonConstantsHeuristic;
import com.linkedin.drelephant.util.Utils;


/**
 * This class does baseline computation once the job is enabled for auto tuning.
 * It takes average resource usage and execution time for last 30 jobs to get the baseline.
 */
public class BaselineComputeUtil {

  public static Integer numJobsForBaselineDefault = 30;
  public Integer numJobsForBaseline = null;
  private final Logger logger = Logger.getLogger(getClass());
  public static final String BASELINE_EXECUTION_COUNT = "baseline.execution.count";

  public BaselineComputeUtil() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    numJobsForBaseline = Utils.getNonNegativeInt(configuration, BASELINE_EXECUTION_COUNT, numJobsForBaselineDefault);
  }

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

  private List<TuningJobDefinition> getJobForBaselineComputation() {
    logger.info("Starting Computing baseline for jobs: ");

    List<TuningJobDefinition> tuningJobDefinitions =
        TuningJobDefinition.find.where().eq(TuningJobDefinition.TABLE.averageResourceUsage, null).findList();
    logger.debug("Computing baseline for jobs: " + Json.toJson(tuningJobDefinitions));
    logger.info("Baseline computing finished.");
    return tuningJobDefinitions;
  }

  private void updateBaselineForJob(TuningJobDefinition tuningJobDefinition) {

    logger.debug("Computing baseline for jobs: " + Json.toJson(tuningJobDefinition));

    String sql =
        "SELECT AVG(resource_used) AS resource_used, AVG(execution_time) AS execution_time FROM "
            + "(SELECT job_exec_id, SUM(resource_used) AS resource_used, "
            + "SUM(finish_time - start_time - total_delay) AS execution_time, " + "MAX(start_time) AS start_time "
            + "FROM yarn_app_result WHERE job_def_id=:jobDefId " + "GROUP BY job_exec_id "
            + "ORDER BY start_time DESC " + "LIMIT :num) temp";

    logger.debug("Running query for baseline computation " + sql);

    SqlRow baseline =
        Ebean.createSqlQuery(sql).setParameter("jobDefId", tuningJobDefinition.job.jobDefId)
            .setParameter("num", numJobsForBaseline).findUnique();

    Double avgResourceUsage = 0D;
    Double avgExecutionTime = 0D;
    avgResourceUsage = baseline.getDouble("resource_used") / (1024 * 3600);
    avgExecutionTime = baseline.getDouble("execution_time") / (1000 * 60);
    tuningJobDefinition.averageExecutionTime = avgExecutionTime;
    tuningJobDefinition.averageResourceUsage = avgResourceUsage;
    tuningJobDefinition.averageInputSizeInBytes = getAvgInputSizeInBytes(tuningJobDefinition.job.jobDefId);
    logger.debug("Resource usage " + avgResourceUsage + " Execution Time " + avgExecutionTime);
    tuningJobDefinition.update();
  }

  public Long getAvgInputSizeInBytes(String jobDefId) {
    String sql =
        "SELECT AVG(inputSizeInBytes) as avgInputSizeInMB FROM "
            + "(SELECT job_exec_id, SUM(value) inputSizeInBytes, MAX(start_time) AS start_time "
            + "FROM yarn_app_result yar INNER JOIN yarn_app_heuristic_result yahr "
            + "ON yar.id=yahr.yarn_app_result_id " + "INNER JOIN yarn_app_heuristic_result_details yahrd "
            + "ON yahr.id=yahrd.yarn_app_heuristic_result_id " + "WHERE job_def_id=:jobDefId AND yahr.heuristic_name='"
            + CommonConstantsHeuristic.MAPPER_SPEED + "' " + "AND yahrd.name='Total input size in MB' "
            + "GROUP BY job_exec_id ORDER BY start_time DESC LIMIT :num ) temp";

    logger.debug("Running query for average input size computation " + sql);

    SqlRow baseline =
        Ebean.createSqlQuery(sql).setParameter("jobDefId", jobDefId).setParameter("num", numJobsForBaseline)
            .findUnique();
    Double avgInputSizeInBytes = baseline.getDouble("avgInputSizeInMB") * FileUtils.ONE_MB;
    return avgInputSizeInBytes.longValue();
  }
}
