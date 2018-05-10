# --- Support for auto tuning spark
# --- !Ups

ALTER TABLE tuning_algorithm ADD UNIQUE KEY (optimization_algo, optimization_algo_version);
ALTER TABLE tuning_job_execution ADD COLUMN is_param_set_best tinyint(4) default 0;
ALTER TABLE tuning_job_definition ADD COLUMN tuning_disabled_reason text;

INSERT INTO tuning_algorithm VALUES (2, 'SPARK', 'PSO', '2', 'RESOURCE', current_timestamp(0), current_timestamp(0));
INSERT INTO tuning_parameter VALUES (10,'spark.executor.memory',2, 2048, 1024, 10240, 1024, 0, current_timestamp(0), current_timestamp(0));
INSERT INTO tuning_parameter VALUES (11,'spark.memory.fraction',2, 0.6, 0.1, 0.9, 0.1, 0, current_timestamp(0), current_timestamp(0));
INSERT INTO tuning_parameter VALUES (12,'spark.memory.storageFraction', 2, 0.5, 0.1, 0.9, 0.1, 0, current_timestamp(0), current_timestamp(0));
INSERT INTO tuning_parameter VALUES (13,'spark.executor.cores', 2, 1 , 1, 1, 1, 0, current_timestamp(0), current_timestamp(0));
INSERT INTO tuning_parameter VALUES (14,'spark.yarn.executor.memoryOverhead', 2, 384, 384, 1024, 100, 0, current_timestamp(0), current_timestamp(0));

CREATE TABLE IF NOT EXISTS tuning_parameter_constraint (
  id int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'Auto increment unique id',
  job_definition_id int(10) unsigned NOT NULL COMMENT 'foreign key from job_definition table',
  constraint_id int(10) unsigned NOT NULL,
  constraint_type enum('BOUNDARY', 'INTERDEPENDENT') NOT NULL COMMENT 'Constraint type',
  tuning_parameter_id int(10) unsigned NULL COMMENT 'foreign key from tuning_parameter table',
  lower_bound double unsigned NOT NULL,
  upper_bound double unsigned NOT NULL,
  created_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ,
  PRIMARY KEY(id),
  UNIQUE KEY uk1 (job_definition_id, constraint_id),
  CONSTRAINT param_constraints_f1 FOREIGN KEY (job_definition_id) REFERENCES job_definition (id),
  CONSTRAINT param_constraints_f2 FOREIGN KEY (tuning_parameter_id) REFERENCES tuning_parameter (id)
) ENGINE=InnoDB;


# --- !Downs
drop table tuning_parameter_constraint;