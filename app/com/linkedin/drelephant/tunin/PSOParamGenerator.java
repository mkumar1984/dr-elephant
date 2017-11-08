package com.linkedin.drelephant.tunin;

import org.python.core.Py;
import org.python.core.PyInstance;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public class PSOParamGenerator extends ParamGenerator{

    public TunerState generateParamSet(TunerState tunerState){
        JsonNode jsonTunerState = Json.toJson(tunerState);
        String stringTunerState = jsonTunerState.toString();
        try{
            Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python pso_param_generation.py " + stringTunerState);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String updatedStringTunerState = in.readLine();
            tunerState.setStringTunerState(updatedStringTunerState);
        } catch (IOException e){
            System.out.println(e);
        }
        return tunerState;
    }
}
