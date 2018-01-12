#
# Copyright 2016 LinkedIn Corp.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#

# --- !Ups

CREATE TABLE `tuning_algorithm` (
  `id` int(10) unsigned NOT NULL,
  `job_type` enum('PIG','HIVE','SPARK') NOT NULL COMMENT 'Job type e.g. pig, hive, spark',
  `optimization_algo` enum('PSO') NOT NULL COMMENT 'optimization algorithm name e.g. PSO',
  `optimization_algo_version` int(11) NOT NULL COMMENT 'algo version',
  `optimization_metric` enum('RESOURCE','EXECUTION_TIME','TEST_X2') DEFAULT NULL COMMENT 'metric to be optimized',
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `tuning_parameter` (
  `id` int(10) unsigned NOT NULL,
  `param_name` varchar(100) NOT NULL,
  `tuning_algorithm_id` int(10) unsigned NOT NULL,
  `default_value` double NOT NULL,
  `min_value` double NOT NULL,
  `max_value` double NOT NULL,
  `step_size` double NOT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_derived` tinyint(4) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `algo_id` (`tuning_algorithm_id`),
  CONSTRAINT `tuning_parameter_ibfk_1` FOREIGN KEY (`tuning_algorithm_id`) REFERENCES `tuning_algorithm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE `flow_definition` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `flow_def_id` varchar(1000) NOT NULL,
  `flow_def_url` varchar(1000) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `flow_def_id` (`flow_def_id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=latin1;

CREATE TABLE `job_definition` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `job_def_id` varchar(1000) NOT NULL,
  `flow_definition_id` int(10) unsigned NOT NULL,
  `job_name` varchar(1000) DEFAULT NULL,
  `job_def_url` varchar(1000) NOT NULL,
  `scheduler` varchar(100) NOT NULL,
  `username` varchar(100) NOT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_def_id` (`job_def_id`),
  KEY `flow_definition_id` (`flow_definition_id`),
  CONSTRAINT `job_definition_ibfk_1` FOREIGN KEY (`flow_definition_id`) REFERENCES `flow_definition` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=100000 DEFAULT CHARSET=latin1;

CREATE TABLE `tuning_job_definition` (
  `job_definition_id` int(10) unsigned NOT NULL,
  `client` varchar(100) NOT NULL,
  `tuning_algorithm_id` int(10) unsigned NOT NULL,
  `tuning_enabled` tinyint(4) NOT NULL,
  `average_resource_usage` double DEFAULT NULL COMMENT 'average resource usage when optimization started on this job',
  `average_execution_time` double DEFAULT NULL,
  `average_input_size_in_bytes` bigint(20) DEFAULT NULL,
  `allowed_max_resource_usage_percent` double DEFAULT NULL,
  `allowed_max_execution_time_percent` double DEFAULT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `job_definition_id` (`job_definition_id`),
  KEY `tuning_algorithm_id` (`tuning_algorithm_id`),
  CONSTRAINT `tuning_job_definition_ibfk_1` FOREIGN KEY (`job_definition_id`) REFERENCES `job_definition` (`id`),
  CONSTRAINT `tuning_job_definition_ibfk_2` FOREIGN KEY (`tuning_algorithm_id`) REFERENCES `tuning_algorithm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


CREATE TABLE `flow_execution` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `flow_exec_id` varchar(1000) NOT NULL,
  `flow_exec_url` varchar(1000) NOT NULL,
  `flow_definition_id` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `flow_exec_id` (`flow_exec_id`),
  KEY `flow_definition_id` (`flow_definition_id`),
  CONSTRAINT `flow_execution_ibfk_1` FOREIGN KEY (`flow_definition_id`) REFERENCES `flow_definition` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=803 DEFAULT CHARSET=latin1;

CREATE TABLE `job_execution` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Parameter set for an execution',
  `job_exec_id` varchar(1000) DEFAULT NULL,
  `job_exec_url` varchar(1000) DEFAULT NULL,
  `job_definition_id` int(10) unsigned NOT NULL,
  `flow_execution_id` int(10) unsigned DEFAULT NULL,
  `execution_state` enum('SUCCEEDED','FAILED','NOT_STARTED','IN_PROGRESS','CANCELLED') DEFAULT NULL,
  `resource_usage` double DEFAULT NULL,
  `execution_time` double DEFAULT NULL,
  `input_size_in_bytes` bigint(20) DEFAULT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_exec_id_2` (`job_exec_id`),
  KEY `job_exec_id` (`job_exec_id`),
  KEY `job_exec_url` (`job_exec_url`),
  KEY `job_definition_id` (`job_definition_id`),
  KEY `flow_execution_id` (`flow_execution_id`),
  CONSTRAINT `job_execution_ibfk_1` FOREIGN KEY (`job_definition_id`) REFERENCES `job_definition` (`id`),
  CONSTRAINT `job_execution_ibfk_2` FOREIGN KEY (`flow_execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1582 DEFAULT CHARSET=latin1;


CREATE TABLE `tuning_job_execution` (
  `job_execution_id` int(10) unsigned NOT NULL,
  `tuning_algorithm_id` int(10) unsigned NOT NULL,
  `param_set_state` enum('CREATED','SENT','EXECUTED','FITNESS_COMPUTED','DISCARDED') DEFAULT NULL,
  `is_default_execution` tinyint(4) NOT NULL,
  `fitness` double DEFAULT NULL,
  UNIQUE KEY `job_execution_id_2` (`job_execution_id`),
  KEY `job_execution_id` (`job_execution_id`),
  KEY `tuning_algorithm_id` (`tuning_algorithm_id`),
  CONSTRAINT `tuning_job_execution_ibfk_1` FOREIGN KEY (`tuning_algorithm_id`) REFERENCES `tuning_algorithm` (`id`),
  CONSTRAINT `tuning_job_execution_ibfk_2` FOREIGN KEY (`job_execution_id`) REFERENCES `job_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


CREATE TABLE `job_saved_state` (
  `job_definition_id` int(10) unsigned NOT NULL,
  `saved_state` blob NOT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_definition_id`),
  CONSTRAINT `job_saved_state_f1` FOREIGN KEY (`job_definition_id`) REFERENCES `job_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


CREATE TABLE `job_suggested_param_value` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `job_execution_id` int(10) unsigned NOT NULL,
  `tuning_parameter_id` int(10) unsigned NOT NULL,
  `param_value` double NOT NULL,
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_execution_id` (`job_execution_id`,`tuning_parameter_id`),
  KEY `job_suggested_param_values_f2` (`tuning_parameter_id`),
  CONSTRAINT `job_suggested_param_values_f1` FOREIGN KEY (`job_execution_id`) REFERENCES `job_execution` (`id`),
  CONSTRAINT `job_suggested_param_values_f2` FOREIGN KEY (`tuning_parameter_id`) REFERENCES `tuning_parameter` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12245 DEFAULT CHARSET=latin1 ;

# --- !Downs
drop table job_suggested_param_value ;
drop table job_saved_state;
drop table tuning_job_execution;
drop table tuning_job_definition;
drop table job_execution;
drop table flow_execution;
drop table job_definition;
drop table flow_definition;
drop table tuning_parameter;
drop table tuning_algorithm;