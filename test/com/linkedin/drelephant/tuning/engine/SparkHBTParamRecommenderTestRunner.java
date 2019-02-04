/*
 * Copyright 2019 LinkedIn Corp.
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
package com.linkedin.drelephant.tuning.engine;

import static common.DBTestUtil.initDBUtil;
import static common.TestConstants.TEST_SPARK_HBT_PARAM_RECOMMENDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import models.AppResult;


public class SparkHBTParamRecommenderTestRunner implements Runnable {

  public void populateTestData() {
    try {
      initDBUtil(TEST_SPARK_HBT_PARAM_RECOMMENDER);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    populateTestData();
    testGetHBTSuggestionWithExecutorInstancesConfig();
    testGetHBTSuggestionWithoutExecutorInstancesConfig();
  }

  private void testGetHBTSuggestionWithExecutorInstancesConfig() {
    AppResult appResult = AppResult.find.where().idEq("application_1547833800460_664575").findUnique();
    SparkHBTParamRecommender sparkHBTParamRecommender = new SparkHBTParamRecommender(appResult);
    HashMap<String, Double> suggestedParameters = sparkHBTParamRecommender.getHBTSuggestion();
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_MEMORY_KEY, 929L, suggestedParameters
        .get(SparkConfigurationConstants.SPARK_EXECUTOR_MEMORY_KEY).longValue());
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_CORES_KEY, 3,
        suggestedParameters.get(SparkConfigurationConstants.SPARK_EXECUTOR_CORES_KEY).longValue());
    assertTrue(
        "Wrong value for " + SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY,
        suggestedParameters.get(SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY) > 0
            && suggestedParameters.get(SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY) < 1);
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_DRIVER_MEMORY_KEY, 1857L, suggestedParameters
        .get(SparkConfigurationConstants.SPARK_DRIVER_MEMORY_KEY).longValue());
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_INSTANCES_KEY, 34L,
        suggestedParameters.get(SparkConfigurationConstants.SPARK_EXECUTOR_INSTANCES_KEY).longValue());
  }

  private void testGetHBTSuggestionWithoutExecutorInstancesConfig() {
    AppResult appResult = AppResult.find.where().idEq("application_1547833800460_664576").findUnique();
    SparkHBTParamRecommender sparkHBTParamRecommender = new SparkHBTParamRecommender(appResult);
    HashMap<String, Double> suggestedParameters = sparkHBTParamRecommender.getHBTSuggestion();
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_MEMORY_KEY, 1857L, suggestedParameters
        .get(SparkConfigurationConstants.SPARK_EXECUTOR_MEMORY_KEY).longValue());
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_CORES_KEY, 3,
        suggestedParameters.get(SparkConfigurationConstants.SPARK_EXECUTOR_CORES_KEY).longValue());
    assertTrue(
        "Wrong value for " + SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY,
        suggestedParameters.get(SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY) > 0
            && suggestedParameters.get(SparkConfigurationConstants.SPARK_MEMORY_FRACTION_KEY) < 1);
    assertEquals("Wrong value for " + SparkConfigurationConstants.SPARK_DRIVER_MEMORY_KEY, 1857L, suggestedParameters
        .get(SparkConfigurationConstants.SPARK_DRIVER_MEMORY_KEY).longValue());
    assertTrue("Wrong value for " + SparkConfigurationConstants.SPARK_EXECUTOR_INSTANCES_KEY,
        suggestedParameters.containsKey(SparkConfigurationConstants.SPARK_EXECUTOR_INSTANCES_KEY) == false);
  }

}
