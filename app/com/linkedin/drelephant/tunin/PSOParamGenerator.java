package com.linkedin.drelephant.tunin;


import org.python.core.PyInstance;
import org.python.util.PythonInterpreter;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;

public class PSOParamGenerator extends ParamGenerator{

    public TunerState generateParamSet(TunerState jobTunerState){

        JsonNode jsonJobTunerState = Json.toJson(jobTunerState);
        PythonInterpreter interpreter = new PythonInterpreter();


    }
}
