package com.linkedin.drelephant.tunin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import com.linkedin.drelephant.configurations.scheduler.SchedulerConfigurationData;
import com.linkedin.drelephant.exceptions.WorkflowClient;
import com.linkedin.drelephant.exceptions.azkaban.AzkabanWorkflowClient;
import com.linkedin.drelephant.util.InfoExtractor;

public class AzkabanJobStatusUtil {
  private AzkabanWorkflowClient _workflowClient;
  private String scheduler="azkaban";
  private static String USERNAME = "username";
  private static String PRIVATE_KEY  = "private_key";
  private static String PASSWORD = "password";


  public AzkabanJobStatusUtil(String url, String token)
  {
    // create a new workflow client
    _workflowClient = (AzkabanWorkflowClient)InfoExtractor.getWorkflowClientInstance(scheduler, url);
    // get the schedulerData
    SchedulerConfigurationData schedulerData = InfoExtractor.getSchedulerData(scheduler);

    if(schedulerData==null) {
      throw new RuntimeException(String.format("Cannot find scheduler %s", scheduler));
    }

    if(!schedulerData.getParamMap().containsKey(USERNAME)) {
      throw new RuntimeException(String.format("Cannot find username for login"));
    }

    String username = schedulerData.getParamMap().get(USERNAME);

    if(schedulerData.getParamMap().containsKey(PRIVATE_KEY)) {
      _workflowClient.login(username, new File(schedulerData.getParamMap().get(PRIVATE_KEY)));
    } else if (schedulerData.getParamMap().containsKey(PASSWORD)) {
      _workflowClient.login(username, schedulerData.getParamMap().get(PASSWORD + token));
    } else {
      throw new RuntimeException("Neither private key nor password was specified");
    }
  }

  public Map<String, String> getJobsFromFlow(String execUrl) throws MalformedURLException, URISyntaxException
  {
      _workflowClient.setURL(execUrl);
      return _workflowClient.getJobsFromFlow();
  }
}
