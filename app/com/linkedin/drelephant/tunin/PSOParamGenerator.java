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
import play.libs.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PSOParamGenerator extends ParamGenerator{

    private static  final String PARAMS_TO_TUNE_FIELD_NAME = "parametersToTune";
    private static final String PYTHON_ROOT_DIR = "/home/aragrawa/virtualenvs/auto-tuning/bin/python";
    private static final String TUNING_SCRIPT_PATH = "/home/aragrawa/development/production/dr-elephant/app/com/linkedin/drelephant/tunin/pso_param_generation.py";

    public TunerState generateParamSet(TunerState tunerState){

        TunerState newTunerState = new TunerState();
        newTunerState.setTuningJob(tunerState.getTuningJob());
        newTunerState.setParametersToTune(tunerState.getParametersToTune());

        JsonNode jsonTunerState = Json.toJson(tunerState);
        String parametersToTune = jsonTunerState.get(PARAMS_TO_TUNE_FIELD_NAME).toString();

        String stringTunerState = tunerState.getStringTunerState();
        stringTunerState = stringTunerState.replaceAll("\\s+","");

        try{
            Process p = Runtime.getRuntime().exec(PYTHON_ROOT_DIR + " " + TUNING_SCRIPT_PATH + " " + stringTunerState+ " " + parametersToTune);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String updatedStringTunerState = in.readLine();
            newTunerState.setStringTunerState(updatedStringTunerState);
        } catch (IOException e){
            System.out.println(e);
        }
        return newTunerState;
    }

}
