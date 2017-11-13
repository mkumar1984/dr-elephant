drop table job_suggested_param_values;
drop table job_execution;
drop table job;
drop table algo_param;
drop table algo;
drop table job_saved_state;
show tables;

desc job;
desc algo;
desc algo_param;
desc job_execution;
desc job_suggested_param_value;

select * from algo;
select * from job;
select * from algo_param;
select * from job_execution;
select * from job_suggested_param_value;

insert into algo (algo_id, job_type, optimization_algo, optimization_algo_version, optimization_metric) values (0, "PIG", "PSO", 1, "RESOURCE");
insert into algo (algo_id, job_type, optimization_algo, optimization_algo_version, optimization_metric) values (0, "HIVE", "PSO", 1, "RESOURCE");
insert into algo (algo_id, job_type, optimization_algo, optimization_algo_version, optimization_metric) values (0, "SPARK", "PSO", 1, "RESOURCE");

insert into algo_param values (0, "param1", 1, "DOUBLE", "3", "0", "5", "1", current_timestamp, current_timestamp);
insert into algo_param values (0, "param2", 1, "DOUBLE", "10", "1", "16", "0.5", current_timestamp, current_timestamp);
insert into algo_param values (0, "param3", 1, "INT", "1000", "250", "2000", "125", current_timestamp, current_timestamp);

insert into job values (0,"A1", "B1", "C1", "D1", 2, "UMP", "mkumar1", true, null, null, null, null, false, current_timestamp, current_timestamp)
insert into job values (0,"A2", "B2", "C2", "D2", 1, "UMP", "mkumar1", true, null, null, null, null, false, current_timestamp, current_timestamp)

insert into job_execution values (0, 100001, 1, "CREATED", false, null, null, "NOT_STARTED", null, null, null, null, current_timestamp, current_timestamp)
insert into job_execution values (0, 100001, 1, "CREATED", false, null, null, "NOT_STARTED", null, null, null, null, current_timestamp, current_timestamp)
insert into job_execution values (0, 100001, 1, "CREATED", false, null, null, "NOT_STARTED", null, null, null, null, current_timestamp, current_timestamp)

insert into job_suggested_param_value values (1, 1, "4", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (1, 2, "14", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (1,  3, "500", current_timestamp, current_timestamp);

insert into job_suggested_param_value values (2, 1, "3", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (2, 2, "11", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (2,  3, "125", current_timestamp, current_timestamp);

insert into job_suggested_param_value values (3, 1, "2", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (3, 2, "9", current_timestamp, current_timestamp);
insert into job_suggested_param_value values (3,  3, "725", current_timestamp, current_timestamp);

CREATE TABLE IF NOT EXISTS algo (
  algo_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Algo ID of the job which need to be optimized' ,
  job_type ENUM('PIG', 'HIVE', 'SPARK') NOT NULL COMMENT 'Job type e.g. pig, hive, spark', 
  optimization_algo enum ('PSO')  NOT NULL COMMENT 'optimization algorithm name e.g. PSO',
  optimization_algo_version int NOT NULL COMMENT 'algo version', 
  optimization_metric enum('RESOURCE', 'EXECUTION_TIME', 'TEST_X2') COMMENT 'metric to be optimized',
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(algo_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job (
  job_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Job ID of the job which need to be optimized' ,
  project_name VARCHAR(1000)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  host_name VARCHAR(1000)  NOT NULL COMMENT 'Host where the job is scheduled. This is kept for uniqueness as different colo might have same jobs scheduled',
  flow_def_id VARCHAR(1000)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  job_def_id VARCHAR(1000)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  algo_id INT UNSIGNED NOT NULL,
  scheduler VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  user VARCHAR(100)  NOT NULL COMMENT 'Name of the project, In case of Azkaban this is azkaban project ',
  tuning_enabled TINYINT NOT NULL COMMENT 'Tuninig is enabled or not for this job',  
  average_resource_usage double COMMENT 'average resource usage when optimization started on this job',
  average_execution_time double,
  allowed_max_resource_usage_percent double, 
  allowed_max_execution_time_percent double,
  deleted TINYINT,
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(job_id),
  CONSTRAINT job_f1 FOREIGN KEY (algo_id) REFERENCES algo (algo_id)
) ENGINE=InnoDB ;

ALTER TABLE job AUTO_INCREMENT = 100000;

CREATE TABLE IF NOT EXISTS algo_param (
  param_id INT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Unique Param ID of the parameter which need to be optimized' ,
  param_name varchar(100) NOT NULL,
  algo_id INT UNSIGNED NOT NULL,
  param_value_type enum ('INT', 'FLOAT', 'DOUBLE', 'BOOLEAN') NOT NULL,
  default_value varchar(100) NOT NULL,
  min_value varchar(100) NOT NULL,
  max_value varchar(100) NOT NULL,
  step_size varchar(100) NOT NULL,
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(param_id),
  INDEX(algo_id),
  CONSTRAINT algo_param_f1 FOREIGN KEY (algo_id) REFERENCES algo (algo_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job_execution (
  param_set_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT 'Parameter set for an execution' ,
  job_id INT UNSIGNED NOT NULL,
  algo_id INT UNSIGNED NOT NULL,
  param_set_state enum ('CREATED', 'SENT', 'EXECUTED', 'FITNESS_COMPUTED', 'DISCARDED'), 
  is_default_execution TINYINT NOT NULL,
  job_execution_id varchar(1000),
  flow_execution_id varchar(1000),
  execution_state enum('SUCCEDED', 'FAILED', 'NOT_STARTED', 'IN_PROGRESS'), 
  resource_usage double, 
  execution_time double,
  input_size_in_mb BIGINT, 
  cost_metric DOUBLE, 
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(param_set_id),
  INDEX(job_id),
  INDEX(job_execution_id),
  CONSTRAINT job_execution_f1 FOREIGN KEY (job_id) REFERENCES job (job_id),
  CONSTRAINT job_execution_f2 FOREIGN KEY (algo_id) REFERENCES algo (algo_id)
) ENGINE=InnoDB ;

CREATE TABLE IF NOT EXISTS job_suggested_param_value (
	param_set_id BIGINT UNSIGNED NOT NULL,
	param_id INT UNSIGNED NOT NULL, 
	param_value varchar(100) NOT NULL,
	created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(param_set_id, param_id),
  CONSTRAINT job_suggested_param_values_f1 FOREIGN KEY (param_set_id) REFERENCES job_execution (param_set_id),
  CONSTRAINT job_suggested_param_values_f2 FOREIGN KEY (param_id) REFERENCES algo_param (param_id)
) ENGINE=InnoDB ;


CREATE TABLE IF NOT EXISTS job_saved_state (
  job_id INT UNSIGNED NOT NULL,
  saved_state BLOB NOT NULL,
  created_ts TIMESTAMP DEFAULT now(), 
  updated_ts TIMESTAMP DEFAULT now() ON UPDATE now(),
  PRIMARY KEY(job_id),
  CONSTRAINT job_saved_state_f1 FOREIGN KEY (job_id) REFERENCES job (job_id)
) ENGINE=InnoDB ;