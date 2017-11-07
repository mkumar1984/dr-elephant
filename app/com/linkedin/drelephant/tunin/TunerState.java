package com.linkedin.drelephant.tunin;

import models.AlgoParam;
import models.Job;

import java.util.List;

public class TunerState {
    private Job _tuningJob;

//    private List<Particle> _archive;
//    private List<Particle> _previousPopulation;
//    private List<List<Integer>> _randomState;
//    private List<Particle> _currentPopulation;
    private String _stringTunerState;

    // Todo: The following won't be required once, algo class contains the list of parameters
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
//
//    public void setPreviousPopulation(List<Particle> previousPopulation){
//        this._previousPopulation = previousPopulation;
//    }
//
//    public List<Particle> getPreviousPopulation(){
//        return _previousPopulation;
//    }
//
//    public void setCurrentPopulation(List<Particle> currentPopulation){
//        this._currentPopulation = currentPopulation;
//    }
//
//    public List<Particle> getCurrentPopulation(){
//        return _currentPopulation;
//    }
//
//    public void setRandomState(List<List<Integer>> randomState){
//        this._randomState = randomState;
//    }
//
//    public List<List<Integer>> getRandomState(){
//        return _randomState;
//    }

    public void setParametersToTune(List<AlgoParam> parameters){ this._parametersToTune = parameters; }

    public List<AlgoParam> getParametersToTune(){ return _parametersToTune; }
}
