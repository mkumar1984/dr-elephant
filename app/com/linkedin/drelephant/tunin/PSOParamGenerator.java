package com.linkedin.drelephant.tunin;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PSOParamGenerator extends ParamGenerator{

    public TunerState generateParamSet(TunerState tunerState){
        JsonNode jsonTunerState = Json.toJson(tunerState);
        String stringTunerState = jsonTunerState.get("stringTunerState").toString();
        String parametersToTune = jsonTunerState.get("parametersToTune").toString();
        try{
            Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python pso_param_generation.py " +stringTunerState+" "+parametersToTune);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String updatedStringTunerState = in.readLine();
            tunerState.setStringTunerState(updatedStringTunerState);
        } catch (IOException e){
            System.out.println(e);
        }
        return tunerState;
    }

}
