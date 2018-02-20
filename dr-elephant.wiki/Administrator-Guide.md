#### Table of Contents
* [System Requirements](#System-Requirements)
* [Deploying Dr. Elephant](#Deploying-dr-elephant-on-the-cluster)
  * [Deploying Configurations](#deploying-configurations)
    * [Airflow and Oozie Configuration](#airflow-and-oozie-configuration)
  * [Deploying the Binary](#deploying-the-binary)
  * [Starting/Stopping Dr. Elephant](#startingstopping-dr-elephant)
  
## System Requirements
Dr. Elephant relies on the YARN Resource Manager and the Job History Server to fetch the applications and their details. The YARN applications and their analysis details will be stored in a backend database, currently configured for MySQL. So before you can run Dr. Elephant, MySQL and Hadoop 2 must be installed.

Since [#162](https://github.com/linkedin/dr-elephant/commit/28f4025bbade1be0fc93111ee439859c530a8747), Java 6 support has been removed.

## Deploying Dr. Elephant on the cluster

### Deploying configurations

* Copy the configuration folder to your cluster.
* Set environment variable `$ELEPHANT_CONF_DIR` to point it to the configuration directory.
```shell
$> export ELEPHANT_CONF_DIR=/path/to/conf/dir
```

#### Airflow and Oozie configuration

If you are using the Airflow or Oozie schedulers you will need to edit the `SchedulerConf.xml` file located in your `$ELEPHANT_CONF_DIR`:
* For Airflow, set the `airflowbaseurl` property to point to your Airflow service.
* For Oozie, set the `oozie_api_url` property to point to the API URL of your Oozie scheduler service.
  * For Oozie there are additional optional properties that can be set. Please consult the documentation in the `SchedulerConf.xml` for more information.

### Deploying the binary

* SSH into the cluster machine.
* Switch to the appropriate user.
```shell
sudo -iu <user>
```
* Unzip the dr-elephant release.

### Starting/Stopping Dr. Elephant

* Navigate to the Dr. Elephant release folder. 
* To start dr-elephant, run the start script. The start script takes an optional argument to the application's conf directory. If you have already set up the env variable `$ELEPHANT_CONF_DIR`, just run the start script without any arguments. Otherwise run the start script specifying the path to the conf directory.
```shell
./bin/start.sh [/path/to/app-conf]
```
* To stop dr-elephant run,  
```shell
./bin/stop.sh
```
* To deploy new version, be sure to kill the running process first


