package com.linkedin.drelephant.tunin;


import java.util.List;

public class TunerState {

    private List<Individual> _archieve;
    private List<Individual> _previousPopulation;
    private List<Individual> _currentPopulation;
    private List<List<Integer>> _randomState;

    public void setArchieve(List<Individual> archieve){
        this._archieve = archieve;
    }

    public List<Individual> getArchieve(){
        return _archieve;
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
}
