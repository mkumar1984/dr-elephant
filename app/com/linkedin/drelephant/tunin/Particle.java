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
