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

import models.TuningParameter;
import models.Job;
import java.util.List;

/**
 * This class holds the tuning information for the job.
 */
public class JobTuningInfo {

  private Job _tuningJob;

  //todo: rename it to _tunerState
  private String _stringTunerState; //archive, prev_population, current_population, rnd_state
  private List<TuningParameter> _parametersToTune;

  /**
   * Sets the job being tuned
   * @param tuningJob Job
   */
  public void setTuningJob(Job tuningJob) {
    this._tuningJob = tuningJob;
  }

  /**
   * Returns the job being tuned
   * @return Job
   */
  public Job getTuningJob() {
    return _tuningJob;
  }

  /**
   * Sets the string tuner state
   * @param stringTunerState String tuner state
   */
  public void setStringTunerState(String stringTunerState) {
    this._stringTunerState = stringTunerState;
  }

  /**
   * Returns string tuner state
   * @return String tuner state
   */
  public String getStringTunerState() {
    return _stringTunerState;
  }

  /**
   * Sets parameters to tune
   * @param parameters Parameters to tune
   */
  public void setParametersToTune(List<TuningParameter> parameters) { this._parametersToTune = parameters; }

  /**
   * Returns parameters to tune
   * @return Parameters to tune
   */
  public List<TuningParameter> getParametersToTune() { return _parametersToTune; }
}
