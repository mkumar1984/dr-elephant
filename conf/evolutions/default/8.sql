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
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=latin1;


# --- !Downs
DROP TABLE tuning_auto_apply_azkaban_rules;