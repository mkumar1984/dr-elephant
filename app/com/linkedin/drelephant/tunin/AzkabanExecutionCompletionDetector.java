package com.linkedin.drelephant.tunin;

import com.linkedin.drelephant.exceptions.azkaban.AzkabanWorkflowClient;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;


import java.util.ArrayList;
import java.util.List;

public class AzkabanExecutionCompletionDetector extends ExecutionCompletionDetector {

    public boolean isExecutionComplete(TuninJobExecution runningExecution){
        // Todo: Poll azkaban and check if the execution is complet
        // if not completed:
        return false;
    }

}
