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

import com.fasterxml.jackson.databind.JsonNode;
import com.linkedin.drelephant.ElephantContext;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import play.libs.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


/**
 * This class extends ParamGenerator class. It generates parameter suggestion using PSO algorithm,
 */
public class PSOParamGenerator extends ParamGenerator {

  private final Logger logger = Logger.getLogger(PSOParamGenerator.class);
  private static final String PARAMS_TO_TUNE_FIELD_NAME = "parametersToTune";
  public static final String PYTHON_ROOT_DIR_CONF = "python.root.dir";
  public static final String TUNING_SCRIPT_PATH_CONF = "pso.script.path";

  private String PYTHON_ROOT_DIR = null;
  private String TUNING_SCRIPT_PATH = null;

  public PSOParamGenerator() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    PYTHON_ROOT_DIR = configuration.get(PYTHON_ROOT_DIR_CONF);
    TUNING_SCRIPT_PATH = configuration.get(TUNING_SCRIPT_PATH_CONF);
  }

  /**
   * Interacts with python scripts to generate new parameter suggestions
   * @param jobTuningInfo Job tuning information
   * @return Updated job tuning information
   */
  public JobTuningInfo generateParamSet(JobTuningInfo jobTuningInfo) {

    JobTuningInfo newJobTuningInfo = new JobTuningInfo();
    newJobTuningInfo.setTuningJob(jobTuningInfo.getTuningJob());
    newJobTuningInfo.setParametersToTune(jobTuningInfo.getParametersToTune());

    JsonNode jsonTunerState = Json.toJson(jobTuningInfo);
    String parametersToTune = jsonTunerState.get(PARAMS_TO_TUNE_FIELD_NAME).toString();

    String stringTunerState = jobTuningInfo.getStringTunerState();
    stringTunerState = stringTunerState.replaceAll("\\s+", "");

    List<String> error = new ArrayList<String>();

    try {
      logger.debug("Running PSO python script with following parameters: ");
      logger.debug("StringTunerState: " + stringTunerState);
      logger.debug("Parameters to tune: " + parametersToTune);
      Process p =
          Runtime.getRuntime().exec(
              PYTHON_ROOT_DIR + " " + TUNING_SCRIPT_PATH + " " + stringTunerState + " " + parametersToTune);
      BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      String updatedStringTunerState = inputStream.readLine();
      logger.debug("Output from pso script: " + updatedStringTunerState);
      newJobTuningInfo.setStringTunerState(updatedStringTunerState);
      String errorLine;
      while ((errorLine = errorStream.readLine()) != null) {
        error.add(errorLine);
      }

      if (error.size() != 0) {
        logger.error("Error running python script: " + error.toString());
      }
    } catch (IOException e) {
      logger.error("Error in generateParamSet", e);
    }
    return newJobTuningInfo;
  }

}
