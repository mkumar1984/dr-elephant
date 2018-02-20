### Table of contents
* [Speeding up your workflow](#speeding-up-your-workflow)
* [General Advice](#general-advice)
  * [Tuning each step is important](#tuning-each-step-is-important)
  * [File Count vs. Block Count](#file-count-vs-block-count)
  * [Java Task Memory Management](#java-task-memory-management)
    * [UseCompressedoops](#usecompressedoops)
    * [UseCompressedStrings](#usecompressedstrings)
  * [Key tuning parameters](#key-tuning-parameters)
    * [Mappers](#mappers)
    * [Reducers](#reducers)
    * [Compression](#compression)
    * [Memory](#memory)
    * [Advanced](#advanced)
    * [Pig](#pig)
    * [Hive](#hive)


You can use Dr. Elephant to analyze your job (just paste in your Job ID on the Search page), and it will point out areas that could use tuning as well as suggestions on which parameters to adjust.


## Speeding Up Your Workflow

It is expected that most jobs will provide their own configuration specific to that job. In many cases, the default job conf will not provide the best performance for any particular job. Tuning a job requires some effort, but it usually pays off even doing some simple tuning.

In particular, pay attention to settings such as number of maps, number of reducers, io.* settings, memory settings, and number of files generated. Changing some of these to better match your job requirements can have significant savings.

The [Hadoop Map/Reduce Tutorial](http://hadoop.apache.org/docs/current/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html#Task_Execution__Environment) on the Apache site provides very useful and more complete advice.

## General Advice

### Tuning Each Step is Important

For Pig jobs, it can be deadly for performance to just set a default reducer size for the entire pig script. It is usually worth the effort to set PARALLEL for each job that a Pig script will create. Example:
```
memberFeaturesGrouped = GROUP memberFeatures BY memberId PARALLEL 90;
```
### File Count vs. Block Count

In order to prevent Namenode meltdown, bigger files are preferable to smaller files. The NameNode uses around 70 bytes per file and 60 bytes per block. At scale, that 10 byte difference adds up. Plus in many cases, having one large file vs. 10 smaller files will actually be better performance for the entire workflow as well.


### Java Task Memory Management
By default, a maximum of 2G memory per map/reduce task is allocated. For Java-jobs, this usually translates to 1G of heap (Xmx) and .5-1G of non-heap.
For certain jobs this isn't enough memory. 
There are some tricks that one can employ to reduce the footprint:

#### UseCompressedOops
A 32bit JVM uses 32-bit unsigned integers to reference memory locations, which leads to a max heap size of (2^32 -1) = 4GB. A 64-bit JVM uses 64-bit longs instead, which leads to (2^64 - 1) = 16 exabytes of maximum heap space. Awesome! However, by using longs instead of ints, the memory footprint of your application will grow. Some say by even 1.5 times. This could push you beyond that 1G limit of heap.
What can you do about it? All modern JVMs support an option called CompressedOops which represents pointers as 32-bit integers instead of 64-bit longs in some of the cases. This will reduce the memory footprint, though not back to that of a 32-bit. You can turn this option on by doing the following in your azkaban job file:
```
hadoop-inject.mapreduce.(map|reduce).java.opts=-Xmx1G -XX:+UseCompressedOops
```
Note that since azkaban simply overwrites a property instead of adding your portion of configuration to the defaults set in mapred-site.xml etc, you need to make sure that the value you provide here is a combination of any defaults set in mapred-site etc. and your needs. Hence, the "-Xmx1G" portion of the value comes from mapred-site.xml and the remaining portion comes from us.

#### UseCompressedStrings
This will convert String types to a compressed byte[] format. If a job has many String types, this usually results in significant savings. To enable, add -XX:+UseCompressedStrings to your mapreduce.(map|reduce).java.opts setting.
Decrease Virtual Memory
The default virtual memory allocation is 2.1 times the physical memory allocation for a task. If you are receiving errors such as 
```
Container [pid=PID,containerID=container_ID]
 is running beyond virtual memory limits. Current usage: 365.1 MB of 1
GB physical memory used; 3.2 GB of 2.1 GB virtual memory used. Killing
container
```
you can try setting the parameters specified here.

### Key Tuning Parameters
#### Mappers
##### mapreduce.input.fileinputformat.split.minsize
The minimum size chunk that map input should be split into. By increasing this value beyond dfs.blocksize, you can reduce the number of mappers in your job. This is because if say you set the value of mapreduce.input.fileinputformat.split.minsize to 4x dfs.blocksize, then 4 times the size of blocks will be sent to a single mapper, thus, reducing the number of mappers needed to process your input. The value for this property is the number of bytes for input split. Thus to set the value to 256MB, you will specify 268435456 as the value for this property.

##### mapreduce.input.fileinputformat.split.maxsize
The maximum size chunk that map input should be split into when using CombineFileInputFormat or MultiFileInputFormat. By decreasing  this value below dfs.blocksize, you can increase the number of mappers in your job. This is because if say you set the value of mapreduce.input.fileinputformat.split.maxsize to 1/4 dfs.blocksize, then 1/4 the size of a block will be sent to a single mapper, thus, increasing the number of mappers needed to process your input. The value for this property is the number of bytes for input split. Thus to set the value to 256MB, you will specify 268435456 as the value for this property.  Note that if you do not set a max split size when using CombineFileInputFormat, your job will only use 1 mapper (which is probably not what you want)!

#### Reducers

##### mapreduce.job.reduces
One of the biggest killers for workflow performance is the total number of reducers in use. Use too few reducers and the task time is longer than 15 minutes. But too many also causes problems! Determining the number of reducers of individual jobs is a bit of art. But here are some guidelines to think about when picking the number:

* More reducers = more files on the namenode
    Too many small files bogs down the namenode and may ultimately make it crash. So if your reduce output is small (less than 512MB), you want fewer reducers
* More reducers = less time spent processing data
    If you have too few reducers, your reduce tasks may take significantly longer than they should. The faster your jobs' reducers run, the more jobs we can push through the grid.

Shuffling is expensive for large tasks
If you look at the FileSystem Counters for your job, you can see how much data may potentially need to be pushed around from node to node. Let's take a job with 20 reducers. Here are the FileSystem Counters:

**FileSystemCounter:**
* FILE_BYTES_READ | 2950482442768
* HDFS_BYTES_READ |  1223524334581
* FILE_BYTES_WRITTEN |  5967256875163

We can see our maps (over 10k) generated ~5TB of intermediate data. Then let's look at the shuffle time:

**Shuffle Finished:**

17-Aug-2010 | 13:32:05 | ( 1hrs, 29mins, 56 sec)

**Sort Finished:**

17-Aug-2010 | 14:18:35 | (46mins, 29sec)

You can see that this job was pushing around 5TB and it took 2 hours for that to happen. Then another 46 minutes for the sort to happen. That's a lot of time! We want to target tasks to finish within the 5-15 minute mark. We're clearly way over that. So let's apply some math: 20 reducers is 360 minutes. 200 reducers should be 36 minutes. 400 reducers should be 18 minutes. So something around that number should be a marked improvement. Bumping the reducers to 500 results in the following:

**Shuffle Finished:**

17-Aug-2010 | 16:32:32 | ( 12 mins, 46 sec)

**Sort Finished:**

17-Aug-2010 | 16:32:37 | (4sec)

Not bad! A bit more tuning and we should be able to get that time down a bit more.
As you can guess, the reverse is also a problem. If your shuffle is taking seconds and your cpu usage is minor, then you might have too many. Again, experimentation is key.

##### mapreduce.job.reduce.slowstart.completedmaps
This setting controls what percentage of maps should be complete before a reducer is started. By default, we set this to .80 (or 80%). For some jobs, it may be better to set this higher or lower. The two factors to consider are:

* how much data will each reducer get
* how long each remaining map will take

If the map output is significant, it is generally recommended that reducers start earlier so that they have a head start processing. If the maps tasks do not produce a lot of data, then it is generally recommended that reducers start later.
A good rough number is to look at the shuffle time for the first reduce to fire off after all the maps are finished. That will represent the time that the reducer takes to get map output. So ideally, reducers will fire off (last map) - (shuffle time).

#### Compression

##### mapreduce.map.output.compress
Setting this to true (default) will shrink map output by compressing it. This will reduce internode transfers, however care must be taken that the time to compress and uncompress is faster than the time to transfer.
For large or highly compress-able intermediate/map output, it is usually beneficial to turn on compression. This can reduce the shuffle time and make disk spills faster.
For small intermediate/map output datasets, turning intermediate output compression off will save the CPU time needed to do the (ultimately useless for this data) compression.
Note that this is different than mapreduce.output.fileoutputformat.compress; that setting controls whether the final job output should be compressed when writing it back to HDFS!

#### Memory

##### mapreduce.(map|reduce).memory.mb
One of the features in newer releases of Hadoop is memory limits. This allows for the system to better manage resources on a busy system. By default, the systems are configured to expect that Java tasks will use 1GB of heap and anywhere from .5-1GB of non-heap memory space. Therefore, the default size of mapreduce.(map|reduce).memory.mb is set to 2GB. In some situations, this is not enough memory. Setting just Xmx will result in more than 2GB and the tasks will get killed. Therefore, in order to request more memory for your task slot you need to adjust both the Xmx value and the mapreduce.(map|reduce).memory.mb value.

#### Advanced

##### Controlling the number of spills / io.sort.record.percent

io.sort.record.percent controls how much of the circular buffer is used for record vs. record metadata. In general, it and a family of tunables are ones to look at when spills are out of control.

Suppose using the log and config xml file from a map task we learn:

| property       | value        |
| ------------- |:-------------:|
| bufstart     | 45633950 |
| bufend     | 68450908     |
| kvstart | 503315      |
| kvend | 838860      |
| length | 838860      |
| io.sort.mb | 256      |
| io.sort.record.percent | .05      |


Using the above numbers:

| property       | value        |
| ------------- |:-------------:|
|io.sort.spill.percent (length-kvstart+kvend) / length) | .8 |
| Size of meta data is (bufend-bufstat) | 22816958 (MB)|
| Records in memory (length-(kvstart+kvend)) | 671087 |
| Average record size (size/records) |34 Bytes |
| Record+Metadata | 50 Bytes |
| Records per io.sort.mb (io.sort.mb/(record+metadata)) | 5.12 million |
| Metadata % in io.sort.mb ((records per io.sort.mb)*metadata/io.sort.mb) | .32 |

We can store plenty of records in our 256MB buffer.
But io.sort.record.percent should be .32, not .05. With .05, we fill the metadata buffer much faster than we fill the record buffer. 

Changing this results in maps running faster and fewer disk spills because io.sort.mb is used more efficiently; we do not hit the 80% mark in the metadata buffer as quickly.

The end result of changing io.sort.record.percent was that many maps did not spill to disk at all and of those that did, many dropped spilled to 55% fewer files. End result: system thrash was reduced--we saved 30% of the CPU and dropped 30 minutes off the runtime!

##### mapreduce.(map|reduce).speculative
Set these properties to false if you want to prevent parallel execution of multiple instances of the same map or reduce task.  You might know that you have data skew so some of your mappers or reducers will take significantly longer.  In this case, you might want to disable speculative execution to prevent spawning lots of unnecessary map and reduce instances.

#### Pig
In Pig, you can set Hadoop and Pig configurations by adding
```
SET <property_name> <property_value>;
```
to your script.  For example, if you are running out of memory in your map tasks, you can increase the map task memory by adding something like
```
SET mapreduce.map.memory.mb 4096;
```
to your Pig script.  In Azkaban, you can also accomplish this by adding
```
jvm.args=-Dmapreduce.map.memory.mb=4096
to your job properties.
```

##### pig.maxCombinedSplitSize / Increasing/Decreasing the Number of Mappers
By default, Pig combines small files (pig.splitCombination is true by default) until the combined split size reaches the HDFS block size of 512 MB.  To increase this further, set pig.maxCombinedSplitSize to a higher value.  See [here](https://pig.apache.org/docs/r0.11.1/perf.html#combine-files) for more details.  You can set this in your Pig script by adding
```
set pig.maxCombinedSplitSize <size-in-bytes>;
```
to the beginning of your Pig script.  If you're executing this Pig script via Azkaban, you can also set this by adding
```
jvm.args=-Dpig.maxCombinedSplitSize=<size-in-bytes>
```
to your job properties.
If your mappers are taking too long and you want to increase the number of mappers (and have each process less data), you must set both
```
set pig.maxCombinedSplitSize <size-in-bytes>;
set mapreduce.input.fileinputformat.split.maxsize <size-in-bytes>;
```
to a value less than 512 MB.
The reason is that Pig will combine splits until `pig.maxCombinedSplitSize` is reached or exceeded, but the split size is calculated by
```
max(mapreduce.input.fileinputformat.split.minsize, min(mapreduce.input.fileinputformat.split.maxsize, dfs.blocksize))
```
which, given some cluster settings:
```
mapreduce.input.fileinputformat.split.minsize=0
mapreduce.input.fileinputformat.split.maxsize is unset
dfs.blocksize=536870912 // 512 MB
will evaluate to 512 MB.
```

##### Number of Reducers
In Pig, you can control the number of reducers on a per-job basis and also optionally set a default number of reducers for your whole script.  See [here](https://pig.apache.org/docs/r0.11.1/perf.html#parallel) for more information.

#### Hive
* mapreduce.input.fileinputformat.split.minsize
* mapreduce.input.fileinputformat.split.maxsize
* mapreduce.input.fileinputformat.split.minsize.per.node
* mapreduce.input.fileinputforomat.split.minsize.per.rack

For Hive, you may need to set all four of these parameters in conjunction to change the split size.  For example:
```
-- The following are the default configurations on our Hadoop clusters.
set mapreduce.input.fileinputformat.split.maxsize                 = 2147483648;
set mapreduce.input.fileinputformat.split.minsize                 = 1073741824;
set mapreduce.input.fileinputformat.split.minsize.per.node        = 1073741824;
set mapreduce.input.fileinputformat.split.minsize.per.rack        = 1073741824;
```
If you want to increase the number of mappers, decrease these sizes.  If you want to decrease the number of mappers, increase these sizes.
