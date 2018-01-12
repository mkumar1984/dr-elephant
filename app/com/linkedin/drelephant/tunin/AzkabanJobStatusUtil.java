/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.tunin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import com.linkedin.drelephant.configurations.scheduler.SchedulerConfigurationData;
import com.linkedin.drelephant.exceptions.WorkflowClient;
import com.linkedin.drelephant.exceptions.azkaban.AzkabanWorkflowClient;
import com.linkedin.drelephant.util.InfoExtractor;

/**
 * This class is azkaban scheduler util for getting job status.
 */
public class AzkabanJobStatusUtil {
  private AzkabanWorkflowClient _workflowClient;
  private String scheduler="azkaban";
  private static String USERNAME = "username";
  private static String PRIVATE_KEY  = "private_key";
  private static String PASSWORD = "password";

  /**
   * Constructor of the class
   * @param url
   */
  public AzkabanJobStatusUtil(String url)
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
      _workflowClient.login(username, schedulerData.getParamMap().get(PASSWORD));
    } else {
      throw new RuntimeException("Neither private key nor password was specified");
    }
  }

  /**
   * Returns the jobs from the flow
   * @param execUrl Execution url
   * @return Jobs from flow
   * @throws MalformedURLException
   * @throws URISyntaxException
   */
  public Map<String, String> getJobsFromFlow(String execUrl) throws MalformedURLException, URISyntaxException
  {
      _workflowClient.setURL(execUrl);
      return _workflowClient.getJobsFromFlow();
  }
}
