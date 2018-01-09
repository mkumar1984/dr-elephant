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

import models.TuningAlgorithm;

/**
 * This class holds the parameters passed to rest api from the client.
 */
public class TuningInput {

    private String flowDefId;
    private String jobDefId;
    private String flowDefUrl;
    private String  jobDefUrl;
    private String flowExecId;
    private String jobExecId;
    private String flowExecUrl;
    private String jobExecUrl;
    private String jobName;
    private String userName;
    private String client;
    private String scheduler;
    private String defaultParams;
    private Boolean isRetry;
    private Boolean skipExecutionForOptimization;
    private String jobType;
    private String optimizationAlgo;
    private String optimizationAlgoVersion;
    private String optimizationMetric;
    private Double allowedMaxResourceUsagePercent;
    private Double allowedMaxExecutionTimePercent;
    private TuningAlgorithm tuningAlgorithm;


    public TuningAlgorithm getTuningAlgorithm() {
      return tuningAlgorithm;
    }

    public void setTuningAlgorithm(TuningAlgorithm tuningAlgorithm) {
      this.tuningAlgorithm = tuningAlgorithm;
    }

    public Boolean getIsRetry() {
      return isRetry;
    }

    public void setIsRetry(Boolean isRetry) {
      this.isRetry = isRetry;
    }

    public Double getAllowedMaxResourceUsagePercent() {
      return allowedMaxResourceUsagePercent;
    }

    public void setAllowedMaxResourceUsagePercent(Double allowedMaxResourceUsagePercent) {
      this.allowedMaxResourceUsagePercent = allowedMaxResourceUsagePercent;
    }

    public Double getAllowedMaxExecutionTimePercent() {
      return allowedMaxExecutionTimePercent;
    }

    public void setAllowedMaxExecutionTimePercent(Double allowedMaxExecutionTimePercent) {
      this.allowedMaxExecutionTimePercent = allowedMaxExecutionTimePercent;
    }

    /**
     * Returns the flow definition id
     * @return Flow definition id
     */
    public String getFlowDefId() {
        return flowDefId;
    }

    /**
     * Sets the flow definition id
     * @param flowDefId Flow definition id
     */
    public void setFlowDefId(String flowDefId) {
        this.flowDefId = flowDefId;
    }

    /**
     * Returns the job definition id
     * @return Job definition id
     */
    public String getJobDefId() {
        return jobDefId;
    }

    /**
     * Sets the job definition id
     * @param jobDefId JOb definition id
     */
    public void setJobDefId(String jobDefId) {
        this.jobDefId = jobDefId;
    }

    /**
     * Returns the flow definition url
     * @return Flow definition url
     */
    public String getFlowDefUrl() {
        return flowDefUrl;
    }

    /**
     * Sets the flow definition url
     * @param flowDefUrl Flow definition url
     */
    public void setFlowDefUrl(String flowDefUrl) {
        this.flowDefUrl = flowDefUrl;
    }

    /**
     * Returns the job definition url
     * @return Job definition url
     */
    public String getJobDefUrl() {
        return jobDefUrl;
    }

    /**
     * Sets the job definition url
     * @param jobDefUrl Job definition url
     */
    public void setJobDefUrl(String jobDefUrl) {
        this.jobDefUrl = jobDefUrl;
    }

    /**
     * Returns the flow execution id
     * @return Flow execution id
     */
    public String getFlowExecId() {
        return flowExecId;
    }

    /**
     * Sets the flow execution id
     * @param flowExecId Flow execution id
     */
    public void setFlowExecId(String flowExecId) {
        this.flowExecId = flowExecId;
    }

    /**
     * Returns the job execution id
     * @return Job execution id
     */
    public String getJobExecId() {
        return jobExecId;
    }

    /**
     * Sets the job execution id
     * @param jobExecId Job execution id
     */
    public void setJobExecId(String jobExecId) {
        this.jobExecId = jobExecId;
    }

    /**
     * Returns the flow execution url
     * @return Flow execution url
     */
    public String getFlowExecUrl() {
        return flowExecUrl;
    }

    /**
     * Sets the flow execution url
     * @param flowExecUrl Flow execution url
     */
    public void setFlowExecUrl(String flowExecUrl) {
        this.flowExecUrl = flowExecUrl;
    }

    /**
     * Returns the job execution url
     * @return Job execution url
     */
    public String getJobExecUrl() {
        return jobExecUrl;
    }

    /**
     * Sets the job execution url
     * @param jobExecUrl Job execution url
     */
    public void setJobExecUrl(String jobExecUrl) {
        this.jobExecUrl = jobExecUrl;
    }

    /**
     * Returns the job name
     * @return Job name
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * Sets the job name
     * @param jobName Job name
     */
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /**
     * Returns the username of the owner of the job
     * @return Username
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Sets the username of the owner of the job
     * @param userName Username
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Returns the client. For example: UMP, Azkaban
     * @return Client
     */
    public String getClient() {
        return client;
    }

    /**
     * Sets the client
     * @param client Client
     */
    public void setClient(String client) {
        this.client = client;
    }

    /**
     * Returns the scheduler
     * @return Scheduler
     */
    public String getScheduler() {
        return scheduler;
    }

    /**
     * Sets the scheduler
     * @param scheduler Scheduler
     */
    public void setScheduler(String scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Returns the default parameters
     * @return default parameters
     */
    public String getDefaultParams() {
        return defaultParams;
    }

    /**
     * Sets the default parameters
     * @param defaultParams default parameters
     */
    public void setDefaultParams(String defaultParams) {
        this.defaultParams = defaultParams;
    }

    /**
     * Returns true if the execution is a retry, false otherwise
     * @return isRetry
     */
    public Boolean getRetry() {
        return isRetry;
    }

    /**
     * Sets the isRetry
     * @param retry
     */
    public void setRetry(Boolean retry) {
        isRetry = retry;
    }

    /**
     * Returns true if this execution is to be skipped for learning by optimization algorithm, false otherwise
     * @return skipExecutionForOptimization
     */
    public Boolean getSkipExecutionForOptimization() {
        return skipExecutionForOptimization;
    }

    /**
     * Sets the skipExecution for optimization param
     * @param skipExecutionForOptimization
     */
    public void setSkipExecutionForOptimization(Boolean skipExecutionForOptimization) {
        this.skipExecutionForOptimization = skipExecutionForOptimization;
    }

    /**
     * Returns the job type
     * @return Job type
     */
    public String getJobType() {
        return jobType;
    }

    /**
     * Sets the job type
     * @param jobType Job type
     */
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    /**
     * Returns the optimization algorithm
     * @return optimization algorithm
     */
    public String getOptimizationAlgo() {
        return optimizationAlgo;
    }

    /**
     * Sets the optimization algorithm
     * @param optimizationAlgo Optimization algorithm
     */
    public void setOptimizationAlgo(String optimizationAlgo) {
        this.optimizationAlgo = optimizationAlgo;
    }

    /**
     * Returns the optimization algorithm version
     * @return Optimization algorithm version
     */
    public String getOptimizationAlgoVersion() {
        return optimizationAlgoVersion;
    }

    /**
     * Sets the optimization algorithm version
     * @param optimizationAlgoVersion Optimization algorithm version
     */
    public void setOptimizationAlgoVersion(String optimizationAlgoVersion) {
        this.optimizationAlgoVersion = optimizationAlgoVersion;
    }

    /**
     * Returns the optimization metric
     * @return Optimization metric
     */
    public String getOptimizationMetric() {
        return optimizationMetric;
    }

    /**
     * Sets the optimization metric
     * @param optimizationMetric Optimization metric
     */
    public void setOptimizationMetric(String optimizationMetric) {
        this.optimizationMetric = optimizationMetric;
    }
}
