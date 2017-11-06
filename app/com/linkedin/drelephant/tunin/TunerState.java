package com.linkedin.drelephant.tunin;

import models.AlgoParam;
import java.util.List;

public class TunerState {

    private List<Individual> _archive;
    private List<Individual> _previousPopulation;
    private List<Individual> _currentPopulation;
    private List<List<Integer>> _randomState;
    private List<AlgoParam> _parameters;

    public void setArchive(List<Individual> archive){
        this._archive = archive;
    }

    public List<Individual> getArchive(){
        return _archive;
    }

    public void setPreviousPopulation(List<Individual> previousPopulation){
        this._previousPopulation = previousPopulation;
    }

    public List<Individual> getPreviousPopulation(){
        return _previousPopulation;
    }

    public void setCurrentPopulation(List<Individual> currentPopulation){
        this._currentPopulation = currentPopulation;
    }

    public List<Individual> getCurrentPopulation(){
        return _currentPopulation;
    }

    public void setRandomState(List<List<Integer>> randomState){
        this._randomState = randomState;
    }

    public List<List<Integer>> getRandomState(){
        return _randomState;
    }

    public void setParameters(List<AlgoParam> parameters){ this._parameters = parameters; }

    public List<AlgoParam> getParameters(){ return _parameters; }
}
