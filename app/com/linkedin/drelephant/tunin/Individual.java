package com.linkedin.drelephant.tunin;

import java.util.List;

public class Individual {

    private List<Float> _candidate;
    private float _fitness;
    private double _birthDate;
    private boolean _maximize;

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

    public void setBirthDate(double birthDate){
        this._birthDate = birthDate;
    }

    public double getBirthDate(){
        return _birthDate;
    }

    public void setMaximize(boolean maximize){
        this._maximize = maximize;
    }

    public boolean getMaximize (){
        return _maximize;
    }
}
