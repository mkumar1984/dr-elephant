package com.linkedin.drelephant.tunin;

public class TuninJobExecution {

    public int _jobId;
    public String _schedulerJobExecutionId;
    public enum TuninJobState {SUCCEEDED, FAILED, RUNNING, SKIPPED};
    public TuninJobState _state;
    public float _resourceUsage;
    public float _executionTime;
    public float _inputSize;

    public int getJobId() {return _jobId;}
    public String getSchedulerExecutionId() {return _schedulerJobExecutionId;}
    public TuninJobState getState() {return _state;}
    public float getResourceUsage() { return _resourceUsage;}
    public float getExecutionTime() { return _executionTime;}
    public float getInputSize() { return _inputSize; }

    public void setJobId(int jobId){
        _jobId = jobId;
    }

    public void setSchedulerExecutionId(String schedulerExecutionId){
        _schedulerJobExecutionId = schedulerExecutionId;
    }

    public void setState(TuninJobState state){
        _state = state;
    }

    public void setResourceUsage(float resourceUsage){
        _resourceUsage = resourceUsage;
    }

    public void setExecutionTime(float executionTime){
        _executionTime = executionTime;
    }

    public void setInputSize(float inputSize){
        _inputSize = inputSize;
    }
}
