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

-- ALTER TABLE job_definition CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE job_execution CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE job_saved_state CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE job_suggested_param_value CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_algorithm CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_job_definition CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_parameter CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_parameter_constraint CHANGE updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ALTER TABLE job_definition DROP updated_ts;
-- ALTER TABLE job_execution DROP updated_ts;
-- ALTER TABLE job_saved_state DROP updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE job_suggested_param_value DROP updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_algorithm CHANGE DROP updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_job_definition DROP updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_parameter DROP updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
-- ALTER TABLE tuning_parameter_constraint DROP updated_ts updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE flow_definition ADD COLUMN created_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE flow_definition ADD COLUMN updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE flow_execution ADD COLUMN created_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE flow_execution ADD COLUMN updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tuning_job_execution ADD COLUMN created_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tuning_job_execution ADD COLUMN updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tuning_job_execution DROP FOREIGN KEY tuning_job_execution_ibfk_2;
ALTER TABLE tuning_job_execution RENAME to job_suggested_param_set;
ALTER TABLE job_suggested_param_set ADD COLUMN are_constraints_violated tinyint(4) default 0 NOT NULL;
ALTER TABLE job_suggested_param_set CHANGE is_default_execution is_param_set_default tinyint(4) NOT NULL;
ALTER TABLE job_suggested_param_set ADD COLUMN job_definition_id int(10) unsigned NOT NULL;
-- UPDATE job_suggested_param_set a INNER JOIN job_execution b on a.job_execution_id = b.id set a.job_definition_id = b.job_definition_id;
ALTER TABLE job_suggested_param_set ADD CONSTRAINT job_suggested_param_set_f1 FOREIGN KEY (job_definition_id) REFERENCES job_definition (id);
ALTER TABLE job_suggested_param_set ADD COLUMN id int(10) DEFAULT 0 NOT NULL;
UPDATE job_suggested_param_set set id = job_execution_id;
ALTER TABLE job_suggested_param_set CHANGE id id int(10) unsigned NOT NULL AUTO_INCREMENT;
ALTER TABLE job_suggested_param_set ADD PRIMARY KEY(id);
ALTER TABLE job_suggested_param_set CHANGE job_execution_id fitness_job_execution_id int(10) unsigned NULL;
ALTER TABLE job_suggested_param_set ADD CONSTRAINT job_suggested_param_set_f2 FOREIGN KEY (fitness_job_execution_id) REFERENCES job_execution(id);

CREATE TABLE IF NOT EXISTS tuning_job_execution_param_set (
  job_suggested_param_set_id int(10) unsigned NOT NULL COMMENT 'foreign key from job_suggested_param_set table',
  job_execution_id int(10) unsigned NOT NULL COMMENT 'foreign key from job_execution table',
  tuning_enabled tinyint(4) NOT NULL COMMENT 'Is tuning enabled for the execution',
  created_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_ts timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY tuning_job_execution_param_set_uk_1 (job_suggested_param_set_id, job_execution_id),
  CONSTRAINT tuning_job_execution_param_set_ibfk_1 FOREIGN KEY (job_suggested_param_set_id) REFERENCES job_suggested_param_set (id),
  CONSTRAINT tuning_job_execution_param_set_ibfk_2 FOREIGN KEY (job_execution_id) REFERENCES job_execution (id)
) ENGINE=InnoDB ;


ALTER TABLE job_suggested_param_value ADD job_suggested_param_set_id int(10) unsigned NOT NULL COMMENT 'foreign key from job_suggested_param_set table';
UPDATE job_suggested_param_value SET job_suggested_param_set_id = job_execution_id;
ALTER TABLE job_suggested_param_value DROP FOREIGN KEY job_suggested_param_values_f1;

ALTER TABLE job_suggested_param_value DROP CONSTRAINT job_execution_id;
-- ALTER TABLE job_suggested_param_value DROP INDEX job_execution_id;

ALTER TABLE job_suggested_param_value DROP COLUMN job_execution_id;
ALTER TABLE job_suggested_param_value ADD UNIQUE KEY job_suggested_param_value_uk_1 (job_suggested_param_set_id, tuning_parameter_id);

# --- !Downs
drop table tuning_job_execution_param_set;
drop table tuning_parameter_constraint;
drop table tuning_job_execution_param_set;
drop table job_suggested_param_set;