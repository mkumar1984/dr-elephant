# --- !Ups

CREATE TABLE `tuning_auto_apply_azkaban_rules` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Auto increment unique id',
  `rule_type` enum('WHITELIST','BLACKLIST') NOT NULL COMMENT 'type of the rule, whitelist for enabling, blacklist for disabling auto tuning',
  `project_name` varchar(700) NOT NULL COMMENT 'Azkaban Project Name to be whitelisted',
  `flow_name_expr` varchar(700) NOT NULL COMMENT 'flow name regular expression for whitelist/blacklist',
  `job_name_expr` varchar(700) NOT NULL COMMENT 'job name regular expression for whitelist/blacklist',
  `job_type` enum('PIG','HIVE','SPARK') NOT NULL COMMENT 'Job type e.g. pig, hive, spark',
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000;

CREATE TABLE `job_execution_performance` (
  `job_definition_id` int(10) unsigned NOT NULL COMMENT 'foreign key from job_definition table',
  `job_suggested_param_set_id` int(10) unsigned NOT NULL,
  `tuning_algorithm_id` int(10) unsigned NOT NULL COMMENT 'foreign key from tuning_algorithm table. algorithm to be used for tuning this job',
  `tuning_enabled` tinyint(4) NOT NULL COMMENT 'auto tuning is enabled or not ',
  `is_param_set_default` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Is parameter set default',
  `is_param_set_best` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Is parameter set best',
  `is_param_set_suggested` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Is parameter set suggested',
  `is_manually_overriden_parameter` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Is parameter set is manually overriden',
  `job_execution_id` int(10) unsigned NOT NULL,
  `execution_state` enum('SUCCEEDED','FAILED','NOT_STARTED','IN_PROGRESS','CANCELLED') NOT NULL COMMENT 'current state of execution of the job ',
  `fitness` double DEFAULT NULL COMMENT 'fitness of this parameter set',
  `fitness_type` enum('RU_PER_GB','HEURISTIC_SCORE') DEFAULT NULL,
  `resource_usage` double DEFAULT NULL COMMENT 'resource usage in GB Hours for this execution of the job',
  `execution_time` double DEFAULT NULL COMMENT 'execution time excluding delay for this execution of the job',
  `input_size_in_bytes` bigint(20) DEFAULT NULL COMMENT 'input size in bytes for this execution of the job',
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_execution_id`,`job_suggested_param_set_id`)
) ENGINE=InnoDB;

CREATE TABLE `job_aggregate_performance` (
  `job_definition_id` int(10) unsigned NOT NULL COMMENT 'foreign key from job_definition table',
  `tuning_algorithm_id` int(10) unsigned NOT NULL COMMENT 'foreign key from tuning_algorithm table. algorithm to be used for tuning this job',
  `default_job_suggested_param_set_id` int(10) unsigned NOT NULL,
  `best_job_suggested_param_set_id` int(10) unsigned NOT NULL,
  `default_job_execution_id` int(10) unsigned NOT NULL,
  `best_job_execution_id` int(10) unsigned NOT NULL,
  `default_param_fitness` double DEFAULT NULL COMMENT 'fitness before tuning started, fitness for default parameter',
  `best_param_fitness` double DEFAULT NULL COMMENT 'fitness after tuning ',
  `fitness_type` enum('RU_PER_GB','HEURISTIC_SCORE') DEFAULT NULL,
  `default_param_resource_usage` double DEFAULT NULL COMMENT 'resource usage in GB Hours for this execution of the job',
  `best_param_resource_usage` double DEFAULT NULL COMMENT 'resource usage in GB Hours for this execution of the job',
  `default_param_execution_time` double DEFAULT NULL COMMENT 'execution time excluding delay for this execution of the job',
  `best_param_execution_time` double DEFAULT NULL COMMENT 'execution time excluding delay for this execution of the job',
  `default_param_ru_per_gb_input` double DEFAULT NULL COMMENT 'resource usage in GB Hours for this execution of the job',
  `best_param_ru_per_gb_input` double DEFAULT NULL COMMENT 'resource usage in GB Hours for this execution of the job',
  `created_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_definition_id`,`tuning_algorithm_id`)
) ENGINE=InnoDB;



# --- !Downs
DROP TABLE tuning_auto_apply_azkaban_rules;
DROP TABLE job_execution_performance;
DROP TABLE job_aggregate_performance;