#### Table Of Contents
* [Dashboard (Home Page)](#dashboard-home-page)
* [Search](#search)
* [Job Details](#job-details)
* [Compare](#compare)
* [Job History Page](#job-history-page)
* [Flow History Page](#flow-history-page)
* [Help](#help)
* [Severities](#severities)

This section shows how to use Dr. Elephant to see, analyze, search, and compare your workflows and jobs.

## Dashboard (Home Page)
The first page that you see after starting starting Dr. Elephant is the dashboard.

![Dashboard](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/dashboard.png)

### Components

#### Cluster Statistics
This includes the latest statistics about the cluster. It tells us the number of jobs analyzed in the last 24 hours, total number of jobs that require some amount of tuning and the number of jobs that are critical and must be tuned.

#### Latest Analysis
This shows the recent applications that were analyzed by Dr. Elephant.

## Search Page

![search page](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/search.png)

The search page allows you to search jobs and flows using the following filters:

* **Job/App ID:** Enter the job id or the application id to search for a specific job. This search returns the Job Details page.
* **Flow Execution ID/URL:** The flow execution id or url(in case of Azkaban) to filter all the jobs triggered by the flow. The result contains all the jobs which belong to a particular flow execution.
* **User:** The username of the user who ran the job.
* **Job Type:** The result shows all the jobs of given type.
* **Severity:** A User can filter jobs based on the [severity](#severities) of the job. For example, when we provide _severe_ as a severity filter, the result will contain a paginated list of jobs which have at least one of its heuristic in severe or critical(higher) state. When a heuristic is provided along with the severity, the result will only apply the severity on the specific Heuristic. If you wish to look at failed jobs, you may specify the Heuristic as '_Exception_' and set the severity to '_moderate_'.
* **Job Finish Date:** You may also filter by the finish time of the jobs. By specifying the 'from' and 'to' fields you can filter by a range of date. The results include 'from' and exclude 'to' ([from, to)). That is, to get all jobs which ran on 15th March, you have to specify the 'from' date as 15th and 'to' date as 16th.

The search fields are actually filters on jobs which can be used together. For e.g. specifying 'user' as '_user1_' and severity as '_critical_' will return all the jobs run by user '_user1_' which are in _critical_ state. 

## Job Details
When you click on one of the jobs from the Dashboard or the Search results, you are taken to the job details page of that job. 
![Job Details](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/jobdetails.png)

### Components:
#### Job footprint with useful links
1. **Job tracker**
This link points to the actual job tracker for the job. When you click on this link, you’ll be redirected to your job on the job tracker. You can then see your job details, logs, map/reduce tasks etc for the job.
1. **Job execution **
This link points to the execution of your job on the scheduler. In case of Azkaban, it points to the executor link for the job.
1. **Job definition**
This link points to the definition of your job on the scheduler. In case of Azkaban, it points you to the job properties page for your job. 
1. **Flow execution**
The flow execution link points to the execution of the whole workflow of your job. In case of Azkaban, it points to the executor link for the workflow.
1. **Flow definition**
This is similar to the job definition, it points to the definition of your workflow. 
1. **Job History**
This link points to the Job history page. You can get more information about the job History page [here](#job-history-page)
1. **Flow History**
This link points to the Flow history page. You can get more information about the Flow history page [here](#flow-history-page)
1. **Metrics**
This block shows the metrics computed for each job by Dr. Elephant. It currently shows Used Resources, Wasted Resources, Runtime and Wait time. 

#### Heuristic Results

When a job is analyzed by Dr. Elephant, it runs all the heuristics on the job. Each heuristic will compute a severity which may be one of _none, low, moderate, severe, critical_. The heuristic severity along with other statistics are shown in this page for the job.  When a job is moderate, severe or critical, a link is provided which points to the help page for the specific heuristic. The help page provides suggestions to the user so that they can improve their job. You can get more details about the help page [here](#help)

## Compare
Using the compare page, you can compare two different flow executions at a job level. When you compare the two executions, the common jobs will be compared and shown at the top. Other jobs will be shown below with jobs from first flow followed by jobs from second flow.

![Compare page](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/compare-page.png)

## Job History Page

The job history page shows a historical comparison of all the recent executions of a particular job.

### Heuristics view

![Job History Page - Heuristics](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/job-history.png)

### Metrics view

![Job History Page - Metrics](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/jobhistorymetrics.png)

### Components

#### Search box
The search box takes a single parameter which is the job's definition ID or the Url in case of Azkaban. It also contains a drop down where user can select Heuristics, Time and Resource. When a user clicks on the search button after specifying the job definition id/url, the job history results are displayed below. You may also navigate to this page from the job details page. The history results contain a performance score graph and all the recent job executions in a tabular fashion.

#### Heuristics Graph (Performance score graph)
The performance score graph is a graph of the recent executions on the X axis against the performance score on the Y axis. When you hover over one of the data points, a small pop-up can be seen which shows the top three poor stages which contribute to the performance score. The performance score is computed using a simple [formula](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#score-calculation). Lower performance score suggests better execution.

#### Metrics Graph (Time and Resources)
The time and resources graph shows the recent executions on the X axis against the metric on the Y axis. When you hover over one of the data points, the metrics for that particular execution are shown as a pop up. You can know more about metrics [here](https://github.com/linkedin/dr-elephant/wiki/Metrics-and-Heuristics#metrics)

#### Tabular representation- Heuristics
Below the performance graph, you can see a tabular representation of your recent executions. The first column contains the execution timestamp which redirects to the job execution on the scheduler. The remaining columns contain the different stages triggered by the job's execution. Each entry for a particular execution of a stage contains multiple colored dots(severity) which represent the heuristics. Hovering over any of the colored dot(heuristic) will open a tool tip which contains the details of the heuristic. 

#### Tabular representation - Metrics
Below the graph, you can see a tabular representation of data points of the graph. Each row represents a particular execution of the job and columns represent the mapreduce stage of the job. Each mapreduce column is divided into further columns where each column represents a metric.

## Flow History Page

The flow history page shows a historical comparison of all the recent executions of a particular flow.

### Heuristics view

![Flow history](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/flow-history.png)

### Metrics view

![Flow History Page - Metrics](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/flowhistorymetrics.png)

### Components
#### Search box
The search box takes a single parameter which is the flow's definition ID or the Url in case of Azkaban. It also contains a drop down where user can select Heuristics, Time and Resource. When a user clicks on the search button after adding the definition URL/ID, a flow history graph is plotted on the performance score graph and all the recent flow executions are shown in the flow executions table.

#### Heuristics Graph (Performance score graph)
The performance score graph is a graph of the recent flow executions on the X axis against the performance score on the Y axis. When you hover over one of the data points, a small pop can be seen which shows the top three poor jobs which contribute to the performance score. The performance score is computed using a simple [formula](#https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#score-calculation). Lower performance score suggests better execution.

#### Metrics Graph (Time and Resources)
The time and resources graph shows the recent executions on the X axis against the metric on the Y axis. When you hover over one of the data points, the metrics for that particular execution are shown as a pop up. You can know more about metrics [here](https://github.com/linkedin/dr-elephant/wiki/Metrics-and-Heuristics#metrics)

#### Tabular representation - Metrics
Below the graph, you can see a tabular representation of data points of the graph. Each row represents a particular execution of the job and columns represent the mapreduce stage of the job. Each mapreduce column is divided into further columns where each column represents a metric.

#### Tabular representation of flow executions and tasks for each job
Below the performance graph, you can see a tabular representation of your recent flow executions. The first column contains the execution timestamp which redirects to the flow's execution on the scheduler. The remaining columns are for the different jobs belonging to the flow. Each entry in the table, contains multiple colored dots representing the different applications/mrjobs triggered by the particular execution of the job(job in the workflow). Hovering over any colored dot will open a tool-tip showing all the heuristics and their severity.

#### Tabular representation - Metrics
Below the graph, you can see a tabular representation of data points of the graph. Each row represents a particular execution of the workflow and columns represent jobs of the workflow. Each job column is divided into further columns where each column represents a metric.

## Help

![Help](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/suggestions.png)

You can navigate to the help page either by clicking on the Help link on the top navigation bar or you can click on the _explain_ link which appears on the job details page when any of the heuristic is in moderate, severe or critical state. The help page provides help on all the different heuristics and provides suggestion on how to tune the job. You can click on any of the heuristic and look at the suggestion for that particular heuristic. The screenshot above shows how the suggestion looks for Mapper memory heuristic. 




## Severities
Severity is a measure of the job's performance. It says how severe a job is in terms of efficiency. There are five severity levels that judge a heuristic/job based on the configured thresholds. The 5 severities in the decreasing order of severeness are
 
CRITICAL > SEVERE > MODERATE > LOW > NONE


| Severity        | Color          | Description  |
| ------------- |:-------------:| :-----|
| CRITICAL     |  ![Alt text](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/critical.png) | The job is in critical state and must be tuned |
| SEVERE      |   ![Alt text](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/severe.png)   |   There is scope for improvement |
| MODERATE | ![Alt text](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/moderate.png)      |    There is scope for further improvement |
| LOW | ![Alt text](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/low.png)     |    There is scope for few minor improvements |
| NONE | ![Alt text](https://github.com/linkedin/dr-elephant/blob/master/images/wiki/none.png)       |    The job is safe. No tuning necessary |