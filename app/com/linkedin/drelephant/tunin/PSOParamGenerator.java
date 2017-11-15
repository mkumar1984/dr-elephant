package com.linkedin.drelephant.tunin;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PSOParamGenerator extends ParamGenerator{

    public TunerState generateParamSet(TunerState tunerState){

        TunerState newTunerState = new TunerState();
        newTunerState.setTuningJob(tunerState.getTuningJob());
        newTunerState.setParametersToTune(tunerState.getParametersToTune());

        JsonNode jsonTunerState = Json.toJson(tunerState);
        String parametersToTune = jsonTunerState.get("parametersToTune").toString();

        String stringTunerState = tunerState.getStringTunerState();
        stringTunerState = stringTunerState.replaceAll("\\s+","");

        try{
            Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python /home/aragrawa/development/production/dr-elephant/app/com/linkedin/drelephant/tunin/pso_param_generation.py " +stringTunerState+" "+parametersToTune);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String updatedStringTunerState = in.readLine();
            newTunerState.setStringTunerState(updatedStringTunerState);
        } catch (IOException e){
            System.out.println(e);
        }
        return newTunerState;
    }

}
