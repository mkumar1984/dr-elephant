#### Table of Contents
* [Metrics](#metrics)
  * [Used Resources](#used-resources)
  * [Wasted Resources](#wasted-resources)
  * [Runtime](#runtime)
  * [Wait time](#wait-time)
* [Heuristics](#heuristics)
  * [Map-Reduce](#map-reduce)
    * [Mapper Data Skew](#mapper-data-skew)
    * [Mapper GC](#mapper-gc)
    * [Mapper Memory](#mapper-memory)
    * [Mapper Speed](#mapper-speed)
    * [Mapper Spill](#mapper-spill)
    * [Mapper Time](#mapper-time)
    * [Reducer Data Skew](#reducer-data-skew)
    * [Reducer GC](#reducer-gc)
    * [Reducer Memory](#reducer-memory)
    * [Reducer Time](#reducer-time)
    * [Shuffle and Sort](#shuffle--sort)
  * [Spark](#spark)
    * [Spark Event Log Limit](#spark-event-log-limit)
    * [Spark Executor Load Balance](#spark-executor-load-balance)
    * [Spark Job Runtime](#spark-job-runtime)
    * [Spark Memory Limit](#spark-memory-limit)
    * [Spark Stage Runtime](#spark-stage-runtime)
  



## Metrics

### Used Resources
The resource usage is the amount of resource used by your job in GB Hours. 

#### Calculation 
We define resource usage of a task as the product of container size of the task and the runtime of the task.
The resource usage of a job can thus be defined as the sum of resource usage of all the mapper tasks and all the reducer tasks.
#### Example
```
Consider a job with: 
4 mappers with runtime {12, 15, 20, 30} mins. 
4 reducers with runtime {10 , 12, 15, 18} mins. 
Container size of 4 GB 
Then, 
Resource used by all mappers: 4 * (( 12 + 15 + 20 + 30 ) / 60 ) GB Hours = 5.133 GB Hours 
Resource used by all reducers: 4 * (( 10 + 12 + 15 + 18 ) / 60 ) GB Hours = 3.666 GB Hours 
Total resource used by the job = 5.133 + 3.6666 = 8.799 GB Hours 
```
### Wasted Resources

This shows the amount of resources wasted by your job in GB Hours or in the form of percentage of resources wasted. 

#### Calculation
```
To calculate the resources wasted, we calculate the following: 
The minimum memory wasted by the tasks (Map and Reduce)
The runtime of the tasks (Map and Reduce)
The minimum memory wasted by a task is equal to the difference between the container size and maximum task memory(peak memory) among all tasks. The resources wasted by the task is then the minimum memory wasted by the task multiplied by the duration of the task. The total resource wasted by the job then will be equal to the sum of wasted resources of all the tasks. 
 
Let us define the following for each task: 

peak_memory_used := The upper bound on the memory used by the task. 
runtime := The run time of the task. 

The peak_memory_used for any task is calculated by finding out the maximum of physical memory(max_physical_memory) used by all the tasks and the virtual memory(virtual_memory) used by the task. 
Since peak_memory_used for each task is upper bounded by max_physical_memory, we can say for each task: 

peak_memory_used = Max(max_physical_memory, virtual_memory/2.1)
Where 2.1 is the cluster memory factor. 

The minimum memory wasted by each task can then be calculated as: 

wasted_memory = Container_size - peak_memory_used 

The minimum resource wasted by each task can then be calculated as: 

wasted_resource = wasted_memory * runtime
```
### Runtime

The runtime metrics shows the total runtime of your job.
#### Calculation
The runtime of the job is the difference between the time when the job was submitted to the resource manager and when the job finished.
#### Example
Let the submit time of a job be 1461837302868 ms 
Let the finish time of the job be 1461840952182 ms 
The runtime of the job will be 1461840952182 - 1461837302868 = 3649314 ms or 1.01 hours

### Wait time
The waittime is the total time spent by the job in the waiting state.

#### Calculation
```
For each task, let us define the following: 

ideal_start_time := The ideal time when all the tasks should have started 
finish_time := The time when the task finished 
task_runtime := The runtime of the task 

- Map tasks
For map tasks, we have 

ideal_start_time := The job submission time 

We will find the mapper task with the longest runtime ( task_runtime_max) and the task which finished last ( finish_time_last ) 
The total wait time of the job due to mapper tasks would be: 

mapper_wait_time = finish_time_last - ( ideal_start_time + task_runtime_max) 

- Reduce tasks
For reducer tasks, we have 

ideal_start_time := This is computed by looking at the reducer slow start percentage (mapreduce.job.reduce.slowstart.completedmaps) and finding the finish time of the map task after which first reducer should have started
We will find the reducer task with the longest runtime ( task_runtime_max) and the task which finished last ( finish_time_last ) 

The total wait time of the job due to reducer tasks would be: 
reducer_wait_time = finish_time_last - ( ideal_start_time + task_runtime_max) 
```

## Heuristics

### Map-Reduce

#### Mapper Data Skew

The mapper data skew heuristic shows whether there is a skewness in the data entering mapper tasks.  This heuristic groups the mappers into two groups; the first group has a set of tasks whose average is less than the second group. 

For example, the first group may contain 900 tasks with an average of mapper data input of 7 MB per task and similarly the second group may contain 1200 task with an average of data input of 500 MB per task.


##### Computation 
The severity of this heuristic is computed by first recursively computing the mean and dividing the tasks into two groups based on the average memory consumed by each group.  The deviation is then found as the ratio of difference between the average memory of the two groups to the minimum of average memory of the two groups. 
```
Let us define the following variables,

    deviation: the deviation in input bytes between two groups
    num_of_tasks: the number of map tasks
    file_size: the average input size of the larger group

    num_tasks_severity: List of severity thresholds for the number of tasks. e.g., num_tasks_severity = {10, 20, 50, 100}
    deviation_severity: List of severity threshold values for the deviation of input bytes between two groups. e.g., deviation_severity: {2, 4, 8, 16}
    files_severity: The severity threshold values for the fraction of HDFS block size. e.g. files_severity = { ⅛, ¼, ½, 1}

Let us define the following functions,

    func avg(x): returns the average of a list x
    func len(x): returns the length of a list x
    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

We’ll compute two groups recursively based on average memory consumed by them. 

Let us call the two groups: group_1 and group_2

Without loss of generality, let us assume that,
    avg(group_1) > avg(group_2) and len(group_1)< len(group_2) then,

    deviation = avg(group_1) - avg(group_2) / min(avg(group_1)) - avg(group_2))
    file_size = avg(group_1)
    num_of_tasks = len(group_0)

The overall severity of the heuristic can be computed as,
    severity = min(
        getSeverity(deviation, deviation_severity)
        , getSeverity(file_size,files_severity)
        , getSeverity(num_of_tasks,num_tasks_severity)
    )
```
##### Configuration Parameters
The values of the threshold variables deviation_severity, num_tasks_severity and files_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).

#### Mapper GC

This analysis gauges your job's GC efficiency. It shows the ratio between the task GC time and task CPU time.

##### Computation
The computation of the severity of this heuristic is done first by finding the average cpu time, average runtime, and average garbage collection time for all the tasks. 
We then find the minimum of the severity due to average runtime and ratio of average garbage collection time to average cpu time. 

```
Let us define the following variables:

    avg_gc_time: average time spent garbage collecting
    avg_cpu_time: average cpu time of all the tasks
    avg_runtime: average runtime of all the tasks
    gc_cpu_ratio: avg_gc_time/ avg_cpu_time

    gc_ratio_severity: List of severity threshold values for the ratio of  avg_gc_time to avg_cpu_time.
    runtime_severity: List of severity threshold values for the avg_runtime.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

    severity = min(getSeverity(avg_runtime, runtime_severity), getSeverity(gc_cpu_ratio, gc_ratio_severity)

```

##### Configuration parameters

The value of different thresholds; gc_ratio_severity and runtime_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics). 



#### Mapper Memory
This heuristic is for mapper memory checking. We check the ratio between your task's consumed memory AND the requested container memory. The consumed memory is the average of each task's [max consumed physical memory snapshot]. The requested container memory is the "mapreduce.map/reduce.memory.mb" config for this job, which is the max physical memory the job can request.

##### Computation

```
Let us define the following variables,

    avg_physical_memory: Average of the physical memories of all tasks.
    container_memory: Container memory

    container_memory_severity: List of threshold values for the average container memory of the tasks.
    memory_ratio_severity: List of threshold values for the ratio of avg_plysical_memory to container_memory

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity can then be computed as,

    severity = min(getSeverity(avg_physical_memory/container_memory, memory_ratio_severity)
               , getSeverity(container_memory,container_memory_severity)
              )
```

##### Configuration Parameters
The values of the threshold variables container_memory_severity and memory_ratio_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).

#### Mapper Speed
This analysis shows the effectiveness of your mapper code. This should allow you to determine if your mapper is CPU-bound or if your mapper is outputting huge amounts of data. This result of the analysis shows problems with mappers with significant slow speeds for the amount of data it needs to read.


##### Computation
The severity of this heuristic is computed by finding the minimum severity of severity due to map speed and the severity due to runtime of map tasks.

```
Let us define the following variables,

    median_speed: median of speeds of all the mappers. The speeds of mappers are found by taking the ratio of input bytes to runtime.
    median_size: median of size of all the mappers
    median_runtime: median of runtime of all the mappers.

    disk_speed_severity: List of threshold values for the median_speed.
    runtime_severity: List of severity threshold values for median_runtime.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

    severity = min(getSeverity(median_speed, disk_speed_severity), getSeverity(median_runtime, median_runtime_severity)
```

##### Configuration parameters

The value of different thresholds; disk_speed_severity and runtime_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).


#### Mapper Spill
This heuristic gauges your mapper performance in a disk I/0 perspective. Mapper spill ratio (spilled records/output records) is a critical indicator to your mapper performance: if the ratio is close to 2, it means each record is spilled to disk twice(once when in-memory sort buffer is almost full, once when merging spilled splits). This usually happens when your mappers have large amount of outputs. 


##### Computation

````
Let us define the following parameters,

    total_spills: The sum of spills from all the map tasks.
    total_output_records: The sum of output records from all the map tasks.
    num_tasks: Total number of tasks.
    ratio_spills: total_spills/ total_output_records

    spill_severity: List of the threshold values for ratio_spills
    num_tasks_severity: List of threshold values for total number of tasks.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

	severity = min(getSeverity(ratio_spills, spill_severity), getSeverity(num_tasks, num_tasks_severity)
```
##### Configuration parameters
The value of different thresholds; spill_severity and num_tasks_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).



#### Mapper Time
This analysis shows how well the number of mappers is adjusted. This should allow you to better tweak the number of mappers for your job. There are two possible situations that needs some tuning.
* Mapper time is too short:  This usually happens when the hadoop job has:
  * A large number of mappers
  * Short mapper avg runtime
  * Small file size 
* Large/ unsplittable files: This happens when the hadoop job has:
  * A small number of mappers
  * Long mapper avg runtime
  * Large file size (a few GB's)

##### Computation

```
Let us define the following variables,

    avg_size: average size of input data for all the mappers
    avg_time: average of runtime of all the tasks.
    num_tasks: total number of tasks.

    short_runtime_severity: The list of threshold values for tasks with short runtime
    long_runtime_severity: The list of threshold values for tasks with long runtime.
    num_tasks_severity: The list of threshold values for number of tasks.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

    short_task_severity = min(getSeverity(avg_time,short_runtime_severity), getSeverity(num_tasks, num_tasks_severity))
    severity = max(getSeverity(avg_size, long_runtime_severity), short_task_severity)

```
##### Configuration parameters
The value of different thresholds; short_runtime_severity , long_runtime_severity and num_tasks_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).


#### Reducer Data Skew
This analysis shows whether there is a data-skew for the data entering reducer tasks. This result of the analysis shows two groups of the spectrum, where the first group has significantly less input data compared to the second group.

##### Computation
The severity of this heuristic is computed by first recursively computing the mean and dividing the tasks into two groups based on the average memory consumed by each group.  The deviation is then found as the ratio of difference between the average memory of the two groups to the minimum of average memory of the two groups. 

```
Let us define the following variables:

    deviation: deviation in input bytes between two groups
    num_of_tasks: number of reduce tasks
    file_size: average of larger group

    num_tasks_severity: List of severity threshold values for the number of tasks.
        e.g. num_tasks_severity = {10,20,50,100}
    deviation_severity: List of severity threshold values for the deviation of input bytes between two groups.
        e.g. deviation_severity = {2,4,8,16}
    files_severity: The severity threshold values for the fraction of HDFS block size
        e.g. files_severity = { ⅛, ¼, ½, 1}

Let us define the following functions:

    func avg(x): returns the average of a list x
    func len(x): returns the length of a list x
    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

We’ll compute two groups recursively based on average memory consumed by them. 

Let us call the two groups: group_1 and group_2

Without loss of generality, let us assume that:
    avg(group_1) > avg(group_2) and len(group_1)< len(group_2)

then,

    deviation = avg(group_1) - avg(group_2) / min(avg(group_1)) - avg(group_2))
    file_size = avg(group_1)
    num_of_tasks = len(group_0)

The overall severity of the heuristic can be computed as, 

    severity = min(getSeverity(deviation, deviation_severity)
        , getSeverity(file_size,files_severity)
        , getSeverity(num_of_tasks,num_tasks_severity)
    )
```
##### Configuration Parameters
The values of the threshold variables deviation_severity, num_tasks_severity and files_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).


#### Reducer GC

This analysis gauges your job's GC efficiency. It shows the ratio between the task GC time and task CPU time.

##### Computation
The computation of the severity of this heuristic is done first by finding the average cpu time, average runtime, and average garbage collection time for all the tasks. 
We then find the minimum of the severity due to average runtime and ratio of average garbage collection time to average cpu time. 

```
Let us define the following variables:

    avg_gc_time: average time spent garbage collecting
    avg_cpu_time: average cpu time of all the tasks
    avg_runtime: average runtime of all the tasks
    gc_cpu_ratio: avg_gc_time/ avg_cpu_time

    gc_ratio_severity: List of severity threshold values for the ratio of  avg_gc_time to avg_cpu_time.
    runtime_severity: List of severity threshold values for the avg_runtime.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

	severity = min(getSeverity(avg_runtime, runtime_severity), getSeverity(gc_cpu_ratio, gc_ratio_severity)

```

##### Configuration parameters
The value of different thresholds; gc_ratio_severity and runtime_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics). 

#### Reducer Memory

This analysis shows the task memory utilization. We check the ratio between your task's consumed memory AND the requested container memory. The consumed memory is the average of each task's [max consumed physical memory snapshot]. The requested container memory is the "mapreduce.map/reduce.memory.mb" config for this job, which is the max physical memory the job can request. 

##### Computation

```
Let us define the following variables,

    avg_physical_memory: Average of the physical memories of all tasks.
    container_memory: Container memory

    container_memory_severity: List of threshold values for the average container memory of the tasks.
    memory_ratio_severity: List of threshold values for the ratio of avg_physical_memory to container_memory

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity can then be computed as,

    severity = min(getSeverity(avg_physical_memory/container_memory, memory_ratio_severity)
               , getSeverity(container_memory,container_memory_severity)
              )
````

##### Configuration Parameters
The values of the threshold variables container_memory_severity and memory_ratio_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics). 

#### Reducer Time

This analysis shows the efficiency of your reducers. This should allow you to better adjust the number of reducers for your job. There are two possible situations that needs some tuning.

* Too many reducers: This happens when the hadoop job has:
  * A large number of reducers
  * Short reducer runtime
* Too few reducer: This happens when the hadoop job has:
  * A small number of reducers
  * Long reducer runtime

##### Computation: 

```
Let us define the following variables,

    avg_size: average size of input data for all the mappers
    avg_time: average of runtime of all the tasks.
    num_tasks: total number of tasks.

    short_runtime_severity: The list of threshold values for tasks with short runtime
    long_runtime_severity: The list of threshold values for tasks with long runtime.
    num_tasks_severity: The number of tasks.

Let us define the following functions,

    func min(x,y): returns minimum of x and y
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity of the heuristic can then be computed as,

    short_task_severity = min(getSeverity(avg_time,short_runtime_severity), getSeverity(num_tasks, num_tasks_severity))
    severity = max(getSeverity(avg_size, long_runtime_severity), short_task_severity)

```

##### Configuration parameters
The value of different thresholds; short_runtime_severity , long_runtime_severity and num_tasks_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).



#### Shuffle & Sort

This analysis shows how much time the reducer spends in shuffle and sort steps versus in the reducer code. This should allow you to understand the efficiency of your reducer.

##### Computation

```
Let’s define following variables,

    avg_exec_time: average time spent in execution by all the tasks.
    avg_shuffle_time: average time spent in shuffling.
    avg_sort_time: average time spent in sorting.

    runtime_ratio_severity: List of threshold values for the ratio of twice of average shuffle or sort time to average execution time.
    runtime_severity: List of threshold values for the runtime for shuffle or sort stages. 

The overall severity can then be found as,

	severity = max(shuffle_severity, sort_severity)

	where shuffle_severity and sort_severity can be found as: 

	shuffle_severity = min(getSeverity(avg_shuffle_time, runtime_severity), getSeverity(avg_shuffle_time*2/avg_exec_time, runtime_ratio_severity))

	sort_severity = min(getSeverity(avg_sort_time, runtime_severity), getSeverity(avg_sort_time*2/avg_exec_time, runtime_ratio_severity))

```

##### Configuration parameters
The value of different thresholds; avg_exec_time , avg_shuffle_time and avg_sort_time are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).


### Spark

#### Spark Event Log Limit
 
Spark's event log passer currently cannot handle very large event log files. It will take too long for Dr Elephant to analyze it that might endanger the entire server. Therefore, currently we sets up a limit (100MB) for event log files, and will by-pass the log-fetching process if the log size exceeds the limit..

##### Computation

This severity of this heuristic is CRITICAL if the data is throttled. Otherwise the severity is NONE.  


#### Spark Executor Load Balance
Unlike Map/Reduce jobs, a Spark application allocates its resources all at once and never release any during the the entire runtime process until everything is finished. It is critical to optimize the load balance situation of executors to avoid excessive usage of the cluster.

##### Computation 

This severity is computed by finding the deviation factor between the peak memory, lowest memory and the average memory. 
```
Let us define the following variables:
    
    peak_memory: List of peak memories for all executors
    durations: List of durations of all executors
    inputBytes: List of input bytes of all executors
    outputBytes: List of output bytes of all executors.

    looser_metric_deviation_severity: List of threshold values for deviation severity, loose bounds.
    metric_deviation_severity: List of threshold values for deviation severity, tight bounds. 

Let us define the following functions:

    func getDeviation(x): returns max(|maximum-avg|, |minimum-avg|)/avg, where
        x = list of values
        maximum = maximum of values in x
        minimum = minimum of values in x
        avg = average of values in x

    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.
    func max(x,y): returns the maximum value of x and y.
    func Min(l): returns the minimum of a list l.

The overall severity can be found as,

    severity = Min( getSeverity(getDeviation(peak_memory), looser_metric_deviation_severity), 
               getSeverity(getDeviation(durations),  metric_deviation_severity),
               getSeverity(getDeviation(inputBytes), metric_deviation_severity),
               getSeverity(getDeviation(outputBytes), looser_metric_deviation_severity). 
               )

```
##### Configuration parameters
The value of different thresholds; looser_metric_deviation_severity and metric_deviation_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).




#### Spark Job Runtime 
This heuristic tunes the Spark job runtime. One Spark application can be broken into multiple jobs and each jobs can be broken into multiple stages.

##### Computation
```

Let us define the following variables,

    avg_job_failure_rate: Average job failure rate
    avg_job_failure_rate_severity: List of threshold values for average job failure rate

Let us define the following variables for each job,

    single_job_failure_rate: Failure rate of a single job
    single_job_failure_rate_severity: List of threshold values for single job failure rate.

The severity of the job can be found as maximum of single_job_failure_rate_severity for all jobs and avg_job_failure_rate_severity.

i.e. severity = max(getSeverity(single_job_failure_rate, single_job_failure_rate_severity),
                    getSeverity(avg_job_failure_rate, avg_job_failure_rate_severity)
                )

where single_job_failure_rate is computed for all the jobs.

```
##### Configuration parameters
The value of different thresholds; single_job_failure_rate_severity and avg_job_failure_rate_severity are easily configurable.. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).




#### Spark Memory Limit
The current Spark applications lacks elasticity while allocating resources. Unlike Mapreduce jobs that allocates resources only for one map-reduce process and releases resources gradually during runtime, Spark allocates all the resources needed for the entire application, and does not release unused ones during the life cycle. Too much memory allocation is dangerous for the entire cluster health. As a result, we are setting limits for both the total memory allowed memory utilization ratio for Spark applications.

##### Computation


```
Let us define the following variables,

    total_executor_memory: total memory of all the executors
    total_storage_memory: total memory allocated for storage by all the executors
    total_driver_memory: total driver memory allocated
    peak_memory: total memory used at peak

    mem_utilization_severity: The list of threshold values for the memory utilization.
    total_memory_severity_in_tb: The list of threshold values for total memory.

Let us define the following functions,

    func max(x,y): Returns maximum of x and y.
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity can then be computed as,

    severity = max(getSeverity(total_executor_memory,total_memory_severity_in_tb),
                   getSeverity(peak_memory/total_storage_memory, mem_utilization_severity)
               )

```
##### Configuration parameters
The value of different thresholds; total_memory_severity_in_tb and mem_utilization_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).


#### Spark Stage Runtime 
Similar to spark job runtime. One Spark application can be broken into multiple jobs and each jobs can be broken into multiple stages.

##### Computation

```

Let us define the following variable for each spark job,

    stage_failure_rate: The stage failure rate of the job
    stagge_failure_rate_severity: The list of threshold values for stage failure rate.

Let us define the following variables for each stage of a spark job,

    task_failure_rate: The task failure rate of the stage
    runtime: The runtime of a single stage

    single_stage_tasks_failure_rate_severity: The list of threshold values for task failure of a stage
    stage_runtime_severity_in_min: The list of threshold values for stage runtime.

Let us define the following functions,

    func max(x,y): returns the maximum value of x and y.
    func getSeverity(x,y): Compares value x with severity threshold values in y and returns the severity.

The overall severity can be found as:

    severity_stage = max(getSeverity(task_failure_rate, single_stage_tasks_faioure_rate_severity),
                   getSeverity(runtime, stage_runtime_severity_in_min)
               )
    severity_job = getSeverity(stage_failure_rate,stage_failure_rate_severity)

    severity = max(severity_stage, severity_job)

where task_failure_rate is computed for all the tasks. 
```

##### Configuration parameters
The value of different thresholds; single_stage_tasks_failure_rate_severity, stage_runtime_severity_in_min and stage_failure_rate_severity are easily configurable. More information on how to configure these parameters can be found [here](https://github.com/linkedin/dr-elephant/wiki/Developer-Guide#configuring-the-heuristics).

