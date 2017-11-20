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

import models.AlgoParam;
import models.Job;

import java.util.List;

public class TunerState {

    private Job _tuningJob;
    private String _stringTunerState; //archive, prev_population, current_population, rnd_state
    private List<AlgoParam> _parametersToTune;

    public void setTuningJob(Job tuningJob){
        this._tuningJob = tuningJob;
    }

    public Job getTuningJob(){
        return _tuningJob;
    }

    public void setStringTunerState(String stringTunerState){
        this._stringTunerState = stringTunerState;
    }

    public String getStringTunerState(){
        return _stringTunerState;
    }


    public void setParametersToTune(List<AlgoParam> parameters){ this._parametersToTune = parameters; }

    public List<AlgoParam> getParametersToTune(){ return _parametersToTune; }
}
