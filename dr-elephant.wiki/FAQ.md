# FAQ
This page contains the frequently asked questions with suggestions and work arounds to help overcome the problems you face.

## 1. Where should Dr. Elephant be deployed?
Dr. Elephant should be deployed on a box having Hadoop setup. Dr. Elephant runs the 'hadoop classpath' command on the cluster and includes all the configurations and jars in its classpath. This is how it talks to the Resource manager and Job History Server. It also runs 'hadoop version' command to verify the hadoop version the cluster is running and to include the java library path.

If you wish to run it on a machine without hadoop, and want to remotely connect to the Hadoop system, try doing the following as a work around, (not tested)

1. Copy all the hadoop 2 configurations and jars to the box.
1. Copy the contents inside $HADOOP_HOME/lib/native to the box.
1. Edit the elephant.conf file and add the property -Djava.library.path=$HADOOP_HOME/lib/native to jvm_args.
1. Edit the start.sh script and make the following changes,
1. Remove lines 131, 143. Especially the part which contains the hadoop version command.
1. Remove -Djava.library.path=$JAVA_LIB_PATH from OPTS in line 145
1. After unzipping the distribution, make the following changes to the dr-elephant executable inside bin,
1. Remove 'hadoop classpath' from app_classpath and replace it with a path to all the configurations and jars you copied in step 1.

## 2. Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000]

I would strongly suggest to make the below change in your 1.sql file of the generated distribution.

Replace lines 49-51, from
```
create index yarn_app_result_i4 on yarn_app_result (flow_exec_id);
create index yarn_app_result_i5 on yarn_app_result (job_def_id);
create index yarn_app_result_i6 on yarn_app_result (flow_def_id);
```
to
```
create index yarn_app_result_i4 on yarn_app_result (flow_exec_id(100));
create index yarn_app_result_i5 on yarn_app_result (job_def_id(100));
create index yarn_app_result_i6 on yarn_app_result (flow_def_id(100));
```
The reason why we haven't updated it in the code is because the h2 in-memory db doesn't support the above syntax and as a result all the fake application tests fail. So you have to compile it, unzip the generated distribution and make the above change in the 1.sql file present in the distribution.

Alternatively, you can add innodb_large_prefix = 1 to my.cnf and restart the mysql server if you wish to retain the index on the entire length.

## 3. [ERROR] - from play in main

You probably have some issue with your mysql setup. To debug further, make the below change in your start.sh script (line 151)
Replace
```
nohup ./bin/dr-elephant ${OPTS} > 2>&1 &
```
with
```
nohup ./bin/dr-elephant ${OPTS} > $project_root/dr.log 2>&1 &
```
Now take a look at the dr.log file generated.

# 4. Database 'default' is in an inconsistent state!

You have some issue with your play evolutions. As a solution, try,

1. Stop Dr. Elephant. Make sure you kill the process. 
   Run ```ps aux | grep elephant;``` to find the pid and then kill the process ```kill -9 <pid>```
1. Delete the database including all its tables.
1. Recreate the database.
1. Enable evolutions if disabled and start Dr. Elephant. To enable evolutions make sure jvm_args is uncommented in the elephant.conf file.
