package com.linkedin.drelephant.tunin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Particle {

    private List<Float> _candidate;
    private float _fitness;
    private double _birthdate;
    private boolean _maximize;

    private Long _paramSetId;

    public void setCandidate(List<Float> candidate){
        this._candidate = candidate;
    }

    public List<Float> getCandidate(){
        return _candidate;
    }

    public void setFitness(float fitness){
        this._fitness = fitness;
    }

    public float getFitness(){
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
