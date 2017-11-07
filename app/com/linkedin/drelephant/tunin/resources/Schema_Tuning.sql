CREATE TABLE IF NOT EXISTS algo (
  algo_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Algo ID of the job which need to be optimized' ,
  job_type ENUM('PIG', 'HIVE', 'SPARK') NOT NULL COMMENT 'Job type e.g. pig, hive, spark', 
  optimization_algo enum ('PSO')  NOT NULL COMMENT 'optimization algorithm name e.g. PSO',
  optimization_algo_version int NOT NULL COMMENT 'algo version', 
  optimization_metric enum('RESOURCE', 'EXECUTION_TIME', 'TEST_X2') COMMENT 'metric to be optimized',
  PRIMARY KEY(algo_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job (
  job_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Job ID of the job which need to be optimized' ,
  project_name VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  host_name VARCHAR(100)  NOT NULL COMMENT 'Host where the job is scheduled. This is kept for uniqueness as different colo might have same jobs scheduled',
  flow_def_id VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  job_def_id VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  algo_id INT UNSIGNED NOT NULL,
  client VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  tuning_enabled TINYINT NOT NULL COMMENT 'Tuninig is enabled or not for this job',  
  average_resource_usage float COMMENT 'average resource usage when optimization started on this job',
  average_execution_time float,
  allowed_max_resource_usage float, 
  allowed_max_execution_time float,
  deleted TINYINT,
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(job_id)
) ENGINE=InnoDB ;

ALTER TABLE job AUTO_INCREMENT = 100000

CREATE TABLE IF NOT EXISTS algo_param (
  param_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Param ID of the parameter which need to be optimized' ,
  param_name varchar(100) NOT NULL,
  algo_id INT UNSIGNED NOT NULL,
  param_value_type enum ('INT', 'FLOAT', 'DOUBLE', 'BOOLEAN') NOT NULL,
  default_value varchar(100) NOT NULL,
  min_value varchar(100) NOT NULL,
  max_value varchar(100) NOT NULL,
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(param_id),
  INDEX(job_type, optimization_algo, optimization_algo_version, optimization_metric)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job_execution (
  param_set_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Parameter set for an execution' ,
  job_id INT UNSIGNED NOT NULL,
  algo_id INT UNSIGNED NOT NULL,
  param_set_state enum ('CREATED', 'SENT', 'EXECUTED', 'FITNESS_COMPUTED', 'DISCARDED'), 
  is_default_execution TINYINT NOT NULL,
  job_execution_id varchar(100),
  flow_execution_id varchar(100),
  execution_state enum('SUCCEDED', 'FAILED', 'NOT_STARTED', 'IN_PROGRESS'), 
  resource_usage float, 
  execution_time float,
  input_size_in_mb BIGINT, 
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(param_set_id),
  INDEX(job_id),
  INDEX(scheduler_job_execution_id),
  CONSTRAINT job_param_set_f1 FOREIGN KEY (job_id) REFERENCES job (job_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job_suggested_param_values (
	param_set_id BIGINT UNSIGNED NOT NULL,
	param_id INT UNSIGNED NOT NULL, 
	param_value varchar(100) NOT NULL,
	created_ts TIMESTAMP DEFAULT now(), 
  	updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
    CONSTRAINT job_suggested_param_values_f1 FOREIGN KEY (param_set_id) REFERENCES job_execution (param_set_id),
    CONSTRAINT job_suggested_param_values_f2 FOREIGN KEY (param_id) REFERENCES algo_param (param_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job_saved_state (
  	job_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Job ID of the job which need to be optimized' ,
	saved_object BLOB NOT NULL,
	created_ts TIMESTAMP DEFAULT now(), 
  	updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
	PRIMARY KEY(job_id),
    CONSTRAINT job_saved_state_f1 FOREIGN KEY (job_id) REFERENCES job (job_id)
) ENGINE=InnoDB ;

