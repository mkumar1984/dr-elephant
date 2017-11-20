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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Particle {

    @JsonProperty("_candidate")
    private List<Double> _candidate;
    private double _fitness;
    private double _birthdate;
    private boolean _maximize;

    @JsonProperty("paramSetId")
    private Long _paramSetId;

    public void setCandidate(List<Double> candidate){
        this._candidate = candidate;
    }

    public List<Double> getCandidate(){
        return _candidate;
    }

    public void setFitness(double fitness){
        this._fitness = fitness;
    }

    public double getFitness(){
        return _fitness;
    }

    public void setBirthdate(double birthDate){
        this._birthdate = birthDate;
    }

    public double getBirthdate(){
        return _birthdate;
    }

    public void setMaximize(boolean maximize){
        this._maximize = maximize;
    }

    public boolean getMaximize (){
        return _maximize;
    }

    public void setPramSetId(Long paramSetId){
        this._paramSetId = paramSetId;
    }

    public Long getParamSetId(){
        return _paramSetId;
    }
}
