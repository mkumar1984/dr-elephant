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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.FlowDefinition;
import models.FlowExecution;
import models.TuningAlgorithm;
import models.TuningJobDefinition;
import models.TuningJobExecution;
import models.TuningParameter;
import models.Job;
import models.JobExecution;
import models.JobExecution.ExecutionState;
import models.TuningJobExecution.ParamSetStatus;
import models.JobSuggestedParamValue;
import org.apache.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.libs.Json;


/**
 * This class processes the API requests and returns param suggestion as response
 */
public class AutoTuningAPIHelper {

  private static final Logger logger = Logger.getLogger (AutoTuningAPIHelper.class);

  //Todo: this will not be hard coded
  TuningAlgorithm _tuningAlgorithm = TuningAlgorithm.find.where ().idEq (1).findUnique ();



  public TuningJobExecution createDefaultJobExecution(TuningJobDefinition tuningJobDefinition){
    TuningJobExecution tuningJobExecutionDefault = TuningJobExecution.find.select ("*")
        .where ()
        .eq (TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + Job.TABLE.id, tuningJobDefinition.job.id)
        .eq (TuningJobExecution.TABLE.isDefaultExecution, true)
        .setMaxRows (1)
        .findUnique ();

    TuningJobExecution tuningJobExecution = new TuningJobExecution ();
    JobExecution jobExecution = new JobExecution ();
    jobExecution.id = 0L;
    jobExecution.job = tuningJobExecutionDefault.jobExecution.job;
    jobExecution.save();
    tuningJobExecution.jobExecution = jobExecution;
    tuningJobExecution.isDefaultExecution = tuningJobExecutionDefault.isDefaultExecution;
    tuningJobExecution.tuningAlgorithm = tuningJobExecutionDefault.tuningAlgorithm;
    tuningJobExecution.paramSetState = ParamSetStatus.CREATED;
    tuningJobExecution.save();

    logger.info("New Default tuning execution: " + Json.toJson (tuningJobExecution));


//    JobExecution prevDefaultExecution = tuningJobExecutionDefault.jobExecution;
//    Long id = prevDefaultExecution.id;

    List<JobSuggestedParamValue> jobSuggestedParamValueList = JobSuggestedParamValue.find.where()
        .eq(JobSuggestedParamValue.TABLE.jobExecution + "." + JobExecution.TABLE.id, tuningJobExecutionDefault.jobExecution.id)
        .findList ();

    for(JobSuggestedParamValue jobSuggestedParamValue: jobSuggestedParamValueList){
      JobSuggestedParamValue jobSuggestedParamValue1 = new JobSuggestedParamValue ();
      jobSuggestedParamValue1.id = 0;
      jobSuggestedParamValue1.jobExecution = jobExecution;
      jobSuggestedParamValue1.paramValue = jobSuggestedParamValue.paramValue;
      jobSuggestedParamValue1.tuningParameter = jobSuggestedParamValue.tuningParameter;
      jobSuggestedParamValue1.save();
    }
    return tuningJobExecution;
  }


  /**
   * Handles the api request and returns param suggestions as response
   * @param tuningInput Rest api parameters
   * @return Parameter Suggestion
   */
  public Map<String, Double> getCurrentRunParameters (TuningInput tuningInput) {

    /**
     * Todo: What if algo related things not given? I feel _tuningAlgorithm related params client se nahi lene chahiye. Why should they care about all this?
     * Todo: use retry, skip execution for optimization
     */

    String jobDefId = tuningInput.getJobDefId ();
    String flowDefId = tuningInput.getFlowDefId ();

    logger.debug ("Starting getCurrentRunParameters");

    TuningJobDefinition tuningJobDefinition = TuningJobDefinition.find.select("*")
        .fetch(TuningJobDefinition.TABLE.job, "*")
        .where ()
        .eq(TuningJobDefinition.TABLE.job + "." + Job.TABLE.jobDefId, jobDefId)
        .eq(TuningJobDefinition.TABLE.tuningEnabled, 1)
        .findUnique ();

    if (tuningJobDefinition == null) {
      logger.info ("New job encountered, creating new entry. ");
      tuningJobDefinition = addNewJobForTuning (tuningInput);
    }

    logger.debug ("Finding execution for job ID " + tuningJobDefinition.job.id);

    TuningJobExecution tuningJobExecution = TuningJobExecution.find.select ("*")
        .fetch (TuningJobExecution.TABLE.jobExecution, "*")
        .fetch(TuningJobExecution.TABLE.jobExecution + "." +JobExecution.TABLE.job, "*")
        .where ()
        .eq (TuningJobExecution.TABLE.jobExecution + "." + JobExecution.TABLE.job + "." + Job.TABLE.id,
            tuningJobDefinition.job.id)
        .eq (TuningJobExecution.TABLE.paramSetState, ParamSetStatus.CREATED)
        .order ()
        .asc (TuningJobExecution.TABLE.jobExecution + "." +JobExecution.TABLE.createdTs)
        .setMaxRows (1)
        .findUnique ();

    if (tuningJobExecution == null) {
        tuningJobExecution = createDefaultJobExecution(tuningJobDefinition);
    }


    //    JobExecution jobExecution = JobExecution.find.select ("*")
//        .fetch (JobExecution.TABLE.job, "*")
//        .where ()
//        .eq (JobExecution.TABLE.job + "." + JobExecution.TABLE.jobId, job.id)
//        .eq (JobExecution.TABLE.paramSetState, ParamSetStatus.CREATED)
//        .order ()
//        .asc (JobExecution.TABLE.createdTs)
//        .setMaxRows (1)
//        .findUnique ();

//    if (jobExecution == null) {
//      jobExecution = JobExecution.find.select ("*")
//          .where ()
//          .eq (JobExecution.TABLE.job + "." + JobExecution.TABLE.jobId, job.id)
//          .eq (JobExecution.TABLE.isDefaultExecution, true)
//          .setMaxRows (1)
//          .findUnique ();
//    }

    logger.debug ("Finding parameters for param set ID " + tuningJobExecution.jobExecution.id);

    List<JobSuggestedParamValue> jobSuggestedParamValues = JobSuggestedParamValue.find.where ()
        .eq (JobSuggestedParamValue.TABLE.jobExecution + "." + JobExecution.TABLE.id, tuningJobExecution.jobExecution.id)
        .findList ();

    logger.debug ("Number of output parameters : " + jobSuggestedParamValues.size ());

    Map<String, Double> paramValues = new HashMap<String, Double> ();

    if (jobSuggestedParamValues != null) {
      for (JobSuggestedParamValue jobSuggestedParamValue : jobSuggestedParamValues) {
        logger.debug ("Param Name is " + jobSuggestedParamValue.tuningParameter.paramName + " And value is "
            + jobSuggestedParamValue.paramValue);
        paramValues.put (jobSuggestedParamValue.tuningParameter.paramName, jobSuggestedParamValue.paramValue);
      }
    }

    updateJobExecutionParameter (tuningJobExecution, tuningInput);

    logger.info ("Finishing getCurrentRunParameters");
    return paramValues;
  }

  /**
   * // Todo; what does this method do?
   * @param tuningJobExecution
   * @param tuningInput
   */
  public void updateJobExecutionParameter (TuningJobExecution tuningJobExecution, TuningInput tuningInput) {

    FlowExecution flowExecution = FlowExecution.find
        .where ()
        .eq(FlowExecution.TABLE.flowExecId, tuningInput.getFlowExecId ())
        .findUnique ();

    if(flowExecution == null) {
      flowExecution = new FlowExecution ();
      flowExecution.flowExecId = tuningInput.getFlowExecId ();
      flowExecution.flowExecUrl = tuningInput.getFlowExecUrl ();
      flowExecution.flowDefinition = tuningJobExecution.jobExecution.job.flowDefinition;
      flowExecution.save ();
    }

    JobExecution jobExecution = tuningJobExecution.jobExecution;
    jobExecution.jobExecId = tuningInput.getJobExecId ();
    jobExecution.jobExecUrl = tuningInput.getJobExecUrl ();
    jobExecution.executionState = ExecutionState.IN_PROGRESS;
    jobExecution.flowExecution = flowExecution;

    logger.info("Saving job execution" + Json.toJson (jobExecution));

    jobExecution.save();

    tuningJobExecution.jobExecution = jobExecution; //todo: needed?
    tuningJobExecution.paramSetState = ParamSetStatus.SENT;
    tuningJobExecution.save();
  }



  /**
   * Add new job for tuning
   * @param tuningInput Tuning input parameters
   * @return Job
   */
  public TuningJobDefinition addNewJobForTuning (TuningInput tuningInput) {

    logger.debug ("Starting addNewJobForTuning");

    Job job = Job.find.select ("*")
        .where ()
        .eq (Job.TABLE.jobDefId, tuningInput.getJobDefId ())
        .findUnique ();

    FlowDefinition flowDefinition = FlowDefinition.find
        .where()
        .eq (FlowDefinition.TABLE.flowDefId, tuningInput.getFlowDefId ())
        .findUnique ();

    if(flowDefinition == null){
      flowDefinition = new FlowDefinition ();
      flowDefinition.flowDefId = tuningInput.getFlowDefId ();
      flowDefinition.flowDefUrl = tuningInput.getFlowDefUrl ();
      flowDefinition.save();
    }

    if(job == null){
      job = new Job ();
      job.jobDefId = tuningInput.getJobDefId ();
      job.scheduler = tuningInput.getScheduler ();
      job.username = tuningInput.getUserName ();
      job.jobName = tuningInput.getJobName ();
      job.jobDefUrl = tuningInput.getJobDefUrl ();
      job.flowDefinition = flowDefinition;
      job.save ();
    }

    String flowDefId = tuningInput.getFlowDefId ();
    String jobDefId = tuningInput.getJobDefId ();
    String flowDefUrl = tuningInput.getFlowDefUrl ();
    String jobDefUrl = tuningInput.getJobDefUrl ();
    String flowExecId = tuningInput.getFlowExecId ();
    String jobExecId = tuningInput.getJobExecId ();
    String flowExecUrl = tuningInput.getFlowExecUrl ();
    String jobExecUrl = tuningInput.getJobExecUrl ();
    String jobName = tuningInput.getJobName ();
    String userName = tuningInput.getUserName ();
    String client = tuningInput.getClient ();
    String scheduler = tuningInput.getScheduler ();
    String defaultParams = tuningInput.getDefaultParams ();
    Boolean isRetry = tuningInput.getRetry ();
    Boolean skipExecutionForOptimization = tuningInput.getSkipExecutionForOptimization ();

    TuningJobDefinition tuningJobDefinition = new TuningJobDefinition ();
    tuningJobDefinition.job = job;
    tuningJobDefinition.client = client;
    tuningJobDefinition.tuningAlgorithm = _tuningAlgorithm; // todo
    tuningJobDefinition.tuningEnabled = 1;
    tuningJobDefinition.save();


    //Job job = insertJob (flowDefId, jobDefId, flowDefUrl, jobDefUrl, jobName, userName, client, scheduler);
    TuningJobExecution tuningJobExecution = insertDefaultJobExecution (job, flowExecId, jobExecId, flowExecUrl, jobExecUrl, flowDefinition);
    insertDefaultParameters (tuningJobExecution.jobExecution, defaultParams);

    logger.debug ("Finishing addNewJobForTuning");
    return tuningJobDefinition;
  }

  /**
   * Inserts new job to database
   * @param flowDefId Flow definition id
   * @param jobDefId Job definition id
   * @param flowDefUrl Flow definition url
   * @param jobDefUrl Job definition url
   * @param jobName Job name
   * @param userName Owner of the job
   * @param client Client whom job belongs
   * @param scheduler Scheduler
   * @return
   */
//  public Job insertJob (String flowDefId, String jobDefId, String flowDefUrl, String jobDefUrl, String jobName,
//      String userName, String client, String scheduler) {
//    logger.debug ("Starting insertJob");
//    Job job = new Job ();
//    job.tuningAlgorithm = _tuningAlgorithm;
//    job.flowDefId = flowDefId;
//    job.jobDefId = jobDefId;
//    job.flowDefUrl = flowDefUrl;
//    job.jobDefUrl = jobDefUrl;
//    job.jobName = jobName;
//    job.username = userName;
//    job.client = client;
//    job.scheduler = scheduler;
//    job.tuningEnabled = true;
//    job.deleted = false;
//    job.save ();
//    logger.debug ("Finishing insertJob. JobID is " + job.id);
//    return job;
//  }

  /**
   * Inserts default job execution in database
   * @param job Job
   * @param flowExecId Flow execution id
   * @param jobExecId Job execution id
   * @param flowExecUrl Flow execution url
   * @param jobExecUrl Job execution url
   * @return default job execution
   */
  public TuningJobExecution insertDefaultJobExecution (Job job, String flowExecId, String jobExecId, String flowExecUrl,
      String jobExecUrl, FlowDefinition flowDefinition) {
    logger.debug ("Starting insertDefaultJobExecution");

    FlowExecution flowExecution = FlowExecution.find
        .where ()
        .eq(FlowExecution.TABLE.flowExecId, flowExecId)
        .findUnique ();

    if(flowExecution == null){
      flowExecution = new FlowExecution ();
      flowExecution.flowExecId = flowExecId;
      flowExecution.flowExecUrl = flowExecUrl;
      flowExecution.flowDefinition = flowDefinition;
      flowExecution.save();
    }

    JobExecution jobExecution = JobExecution.find
        .where ()
        .eq(JobExecution.TABLE.jobExecId, jobExecId)
        .findUnique ();

    if(jobExecution==null){
      jobExecution = new JobExecution ();
      jobExecution.job = job;
      jobExecution.executionState = ExecutionState.NOT_STARTED;
      jobExecution.jobExecId = jobExecId;
      jobExecution.jobExecUrl = jobExecUrl;
      jobExecution.flowExecution = flowExecution;
      jobExecution.save ();
    }

    TuningJobExecution tuningJobExecution = new TuningJobExecution ();
    tuningJobExecution.jobExecution = jobExecution;
    tuningJobExecution.tuningAlgorithm = _tuningAlgorithm;
    tuningJobExecution.paramSetState = ParamSetStatus.CREATED;
    tuningJobExecution.isDefaultExecution = true;
    tuningJobExecution.save ();

    logger.debug ("Finishing insertDefaultJobExecution. Job Execution ID " + jobExecution.jobExecId);


    return tuningJobExecution;
  }

  /**
   * Inserts default execution parameters in database
   * @param jobExecution Job Execution
   * @param defaultParams Default parameters map as string
   */
  public void insertDefaultParameters (JobExecution jobExecution, String defaultParams) {
    @SuppressWarnings("unchecked")
    ObjectMapper mapper = new ObjectMapper ();
    Map<String, Double> paramValueMap = null;
    try {
      // Todo: Safe cast
      paramValueMap = (Map<String, Double>) mapper.readValue (defaultParams, Map.class);
    } catch (Exception e) {
      logger.error (e);
    }
    if (paramValueMap != null) {
      for (Map.Entry<String, Double> paramValue : paramValueMap.entrySet ()) {
        insertExecutionParameter (jobExecution, paramValue.getKey (), paramValue.getValue ());
      }
    } else {
      logger.warn ("ParamValueMap is null ");
    }
  }

  /**
   * Inserts parameter of an execution in database
   * @param jobExecution Job execution
   * @param paramName Parameter name
   * @param paramValue Parameter value
   */
  public void insertExecutionParameter (JobExecution jobExecution, String paramName, Double paramValue) {
    logger.debug ("Starting insertExecutionParameter");
    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue ();
    jobSuggestedParamValue.jobExecution = jobExecution;
    TuningParameter tuningParameter = TuningParameter.find.where ().eq (TuningParameter.TABLE.paramName, paramName).findUnique ();
    if (tuningParameter != null) {
      jobSuggestedParamValue.tuningParameter = tuningParameter;
      jobSuggestedParamValue.paramValue = paramValue;
      jobSuggestedParamValue.save ();
      logger.debug (
          "Finishing insertDefaultJobExecution. Job Execution ID. Param ID " + jobSuggestedParamValue.tuningParameter.id
              + " Param Name: " + jobSuggestedParamValue.tuningParameter.paramName);
    } else {
      logger.warn ("TuningAlgorithm param null " + paramName);
    }
  }
}
