# Dr. Elephant

<p align="center">
  <img src="https://github.com/linkedin/dr-elephant/blob/master/images/wiki/dr-elephant-logo-300x300.png"/>
</p>

**Dr. Elephant** is a performance monitoring and tuning tool for Hadoop and Spark. It automatically gathers a job's metrics, analyzes them, and presents them in a simple way for easy consumption. Its goal is to improve developer productivity and increase cluster efficiency by making it easier to tune the jobs. It analyzes the Hadoop and Spark jobs using a set of pluggable, configurable, rule-based heuristics that provide insights on how a job performed, and then uses the results to make suggestions about how to tune the job to make it perform more efficiently. It also computes a number of metrics for a job which provides valuable information about the job performance on the cluster. 


## Why Dr. Elephant?
Most of the Hadoop optimization tools out there, whether open source or proprietary, are designed to collect system resource metrics and monitor cluster resources. They are focused on simplifying the deployment and management of Hadoop clusters. Very few tools are designed to help Hadoop users optimize their flows. The ones that are available are either inactive or have failed to scale and support the growing Hadoop frameworks. Dr. Elephant supports Hadoop with a variety of frameworks and can be easily extended to newer frameworks. It also has support for Spark. You can plugin and configure as many custom heuristics as you like. It is designed to help the users of Hadoop and Spark understand the internals of their flow and to help them tune their jobs easily.

## Key Features
* Pluggable and configurable rule-based heuristics that diagnose a job;
* Out-of-the-box integration with Azkaban scheduler and support for adding any other Hadoop scheduler, such as Oozie;
* Representation of historic performance of jobs and flows;
* Job-level comparison of flows;
* Diagnostic heuristics for MapReduce and Spark;
* Easily extensible to newer job types, applications, and schedulers;
* REST API to fetch all the information.


## Getting Started

[User Guide](https://github.com/linkedin/dr-elephant/wiki/User-Guide)

[Developer Guide](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide)

[Administrator Guide](https://github.com/linkedin/dr-elephant/wiki/Administrator-Guide)

[Tuning Tips](https://github.com/linkedin/dr-elephant/wiki/Tuning-Tips)

## How does it work?
Dr. Elephant gets a list of all recent succeeded and failed applications, at regular intervals, from the YARN resource manager. The metadata for each application—namely, the job counters, configurations, and the task data—are fetched from the Job History server. Once it has all the metadata, Dr. Elephant runs a set of heuristics on them and generates a diagnostic report on how the individual heuristics and the job as a whole performed. These are then tagged with one of five severity levels, to indicate potential performance problems.

## Use Cases
At Linkedin, developers use Dr. Elephant for a number of different use cases including monitoring how their flow is
performing on the cluster, understanding why their flow is running slow, how and what can be tuned to improve their
flow, comparing their flow against previous executions, troubleshooting etc. Dr. Elephant’s performance green-lighting
is a prerequisite to run jobs on production clusters.

## Sample Job Analysis/Tuning
Dr. Elephant’s home page, or the dashboard, includes all the latest analysed jobs along with some statistics.

<img src="https://github.com/linkedin/dr-elephant/blob/master/images/wiki/dashboard.png" alt="unable to load image" height="350" width="450" align="center" />

Once a job completes, it can be found in the Dashboard, or by filtering on the Search page. One can filter jobs by the
job id, the flow execution url(if scheduled from a scheduler), the user who triggered the job, job finish time, the type
of the job, or even based on severity of the individual heuristics.

<img src="https://github.com/linkedin/dr-elephant/blob/master/images/wiki/search.png" alt="unable to load image" height="350" width="450" align="center" />

The search results provide a high level analysis report of the jobs using color coding to represent severity levels on
how the job and the heuristics performed. The color Red means the job is in critical state and requires tuning while
Green means the job is running efficiently.

Once one filters and identifies one’s job, one can click on the result to get the complete report. The report includes details on each of the individual heuristics and a link, [Explain], which provides suggestions on how to tune the job to improve that heuristic.

<img src="https://github.com/linkedin/dr-elephant/blob/master/images/wiki/jobdetails.png" alt="unable to load image" height="350" width="450" align="center" />

<img src="https://github.com/linkedin/dr-elephant/blob/master/images/wiki/suggestions.png" alt="unable to load image" height="350" width="450" align="center" />