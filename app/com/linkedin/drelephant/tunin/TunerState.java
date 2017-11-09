package com.linkedin.drelephant.tunin;

import models.AlgoParam;
import models.Job;

import java.util.List;

public class TunerState {

    private Job _tuningJob;
    private String _stringTunerState; //archive, prev_population, current_population
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
