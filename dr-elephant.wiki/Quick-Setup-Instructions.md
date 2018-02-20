## Quick Setup Instructions

**Step 1:**
Create an account on github and fork the Dr. Elephant project.

**Step 2:**
Checkout the code.
```
$> git clone https://github.com/<username>/dr-elephant
$> cd dr-elephant*
```

**Step 3:**
You need to have play or activator command installed. Download the activator zip from https://downloads.typesafe.com/typesafe-activator/1.3.12/typesafe-activator-1.3.12.zip, unzip it and add the activator command to your $PATH. For older versions of Play, you need to add the play command instead of activator.

**Step 4:**
Compile Dr. Elephant code and generate the zip. Compile.sh script optionally takes a configuration file which includes the version of Hadoop and Spark to compile with. For instructions check the Developer Guide.
```
$> ./compile.sh
```
After compiling, the distribution is created under dist directory.
```
$> ls dist
dr-elephant*.zip
```

**Step 5:**
Copy the distribution file to the machine where you want to deploy Dr. Elephant.

**Step 6:**
On the machine where you want to deploy Dr. Elephant, make sure the below env variables are set.
```
$> export HADOOP_HOME=/path/to/hadoop/home
$> export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
$> export SPARK_HOME=/path/to/spark/home
$> export SPARK_CONF_DIR=/path/to/conf
```
Add hadoop to the system path because Dr. Elephant uses 'hadoop classpath' to load the right classes.
```
$> export PATH=$HADOOP_HOME/bin:$PATH
```

**Step 7:**
You also need a backend to save the data. Create a mysql database and configure it in the elephant.conf file.
```
# Database configuration
db_url=localhost
db_name=drelephant
db_user=root
db_password=""
```

**Step 8:**
If your cluster is kerberised, then update the keytab user and the keytab file location in the elephant.conf file.

**Step 9:**
To start dr-elephant, run the start script specifying a path to the application's configuration files.

```
$> /bin/start.sh /path/to/app-conf/directory
```
To verify if Dr. Elephant started correctly, check the dr.log file.
```
$> less $DR_RELEASE/dr.log
...
play - database [default] connected at jdbc:mysql://localhost/drelephant?characterEncoding=UTF-8
application - Starting Application...
play - Application started (Prod)
play - Listening for HTTP on /0:0:0:0:0:0:0:0:8080
```
To verify if Dr. Elephant is analyzing jobs correctly correctly check the dr-elephant.log file.
```
$> less $DR_RELEASE/../logs/elephant/dr_elephant.log
```

**Step 10:**
Once the application starts, you can open the UI at ip:port (localhost:8080)

**Step 11:**
To stop dr-elephant run
```
$> bin/stop.sh
```

