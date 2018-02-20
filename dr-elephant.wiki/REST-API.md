### Table of contents

* [Fetch Application by ID] (#fetch-application-by-id)
* [Fetch Application by Job Execution ID] (#fetch-application-by-job-execution-id)
* [Fetch Application by Flow Execution ID] (#fetch-application-by-flow-execution-id)
* [Search or Filter Applications ] (#search-or-filter-applications)
* [Compare Flow Executions] (#compare-flow-executions)

There may be scenarios where a user wants to interact with Dr. Elephant without using the web UI. Dr. Elephant exposes a REST API which can be used to fetch information. 

## Fetch Application by ID
Given a app/job id, this will fetch the application information which includes general information about the app and the heuristic analysis results.
### URI
```
http://<dr-elephant-host:port>/rest/job
```
### Request Parameter
| parameter | description |
| --------- | ----------- |
| id        | The app or job id to search |
### Response Object
| parameter | description |
| ----------| ------------ |
| id          | The application id |
| name        | The name of the application |
| username    | User who submitted the application|
| queueName   | The queue the application was submitted to |
| startTime   | The time in which application started |
| finishTime  | The time in which application finished |
| trackingUrl | The Job history page of the app |
| jobType     | Type of the job e.g. pig|
| severity    | Aggregate severity of all the heuristics. Ranges from 0(LOW) to 4(CRITICAL) |
| score       | The application score which is the sum of heuristic scores |
| workFlowDepth | The application depth in the scheduled flow. Depth starts from 0 |
| scheduler   | The scheduler which triggered the application |
| jobName     | The name of the job in the flow to which this app belongs |
| jobExecId   | A unique reference to a specific execution of the job/action(job in the workflow). |
| flowExecId  | A unique reference to a specific flow execution. |
| jobDefId    | A unique reference to the job in the entire flow independent of the execution. |
| flowDefId   | A unique reference to the entire flow independent of any execution. |
| jobExecUrl  | A url to the job execution on the schedule |
| flowExecUrl | A url to the flow execution on the scheduler |
| jobDefUrl   | A url to the job definition on the scheduler |
| flowDefUrl  | A url to the flow definition on the scheduler |
| yarnAppHeuristicResults | Detailed results on individual heuristics |

## Fetch Application by Job Execution ID
Given a job execution id or job execution url in case of Azkaban, this returns all the applications which were triggered by this job's execution.
### URI
```
http://<dr-elephant-host:port>/rest/jobexec
```
### Request Parameter
| parameter | description |
| --------- | ----------- |
| id        | The job execution id or job execution url(azkaban) to search |
### Response Object
The response object contains list of applications spawned by this job. Each element of this list is of the form shown in response object of ['fetch by application id'](#fetch-application-by-id).

## Fetch Application by Flow Execution ID
Given a flow execution id or flow execution url in case of Azkaban, this returns all the applications which were triggered by this flow's execution.
### URI
```
http://<dr-elephant-host:port>/rest/flowexec
```
### Request Parameter
| parameter | description |
| --------- | ----------- |
| id        | The flow execution id or flow execution url(azkaban) to search |
### Response Object
The response object contains list of applications spawned by this flow. Each element of this list is of the form shown in response object of ['fetch by application id'](#fetch-application-by-id).

## Search or Filter Applications
You can search for a job/flow by providing certain parameters. REST API supports all the search parameters as supported in the UI.
### URI
```
http://<dr-elephant-host:port>/rest/search
```
### Request Parameter
| parameter | description |
| --------- | ----------- |
| id        | The job/app execution id to search. If this is provided, no other parameter is valid |
| flow-exec-id   | The flow execution id or url(azkaban) to search, if this is provided, no other parameter is valid |
| username  | To filter jobs by the user |
| severity  | To filter all jobs based on severity |
| job-type  | The type of jobs to search |
| analysis  | The heuristic name. This goes together with severity. |
| finished-time-begin | The start range of finish time. (Milliseconds since epoch) |
| finished-time-end   | The end range of finish time. (Milliseconds since epoch) |
| started-time-begin  | The start range of start time. (Milliseconds since epoch) |
| started-time-end    | The end range of start time. (Milliseconds since epoch) |
### Response Object
The response object contains a list of applications where each element of this list is of the form shown in response object of ['fetch by application id'](#fetch-application-by-id).

## Compare Flow Executions
Given two different executions(flow execution id), the results will contain a comparison of the two executions at job level.
### URI
```
http://<dr-elephant-host:port>/rest/compare
```
### Request Parameter
| parameter | description |
| --------- | ----------- |
|   flow-exec-id1   | Flow execution id or url(azkaban) of first flow |
|   flow-exec-id2   | Flow execution id or url(azkaban) of second flow |
### Response Object
The response object is of the form:
```
{ 
job_def_id1: {
    flow_exec_id1 : {
        app1_details,
        app2_details
    },
    flow_exec_id2 : {
        app1_details,
        app2_details
    }
},
job_def_id2: {
    flow_exec_id1 : {
        app1_details,
        app2_details,
        app3_details
    },
    flow_exec_id2 : {
        app1_details,
        app2_details
        app3_details
    }
}
}
```
Where app1_details are the details of job as fetched in response object of ['fetch by application id'](#fetch-application-by-id).

