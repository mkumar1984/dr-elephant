import mysql.connector
import json
import re
import smtplib
import logging
import logging.handlers as handlers
import sys
import csv
import os

get_max_execution_id_query="select coalesce(max(job_execution_id), 0) as max_job_execution_id from job_execution_performance"

update_job_execution_performance_query="""INSERT INTO job_execution_performance 
            ( 
                        job_definition_id, 
                        job_suggested_param_set_id, 
                        tuning_algorithm_id, 
                        tuning_enabled, 
                        is_param_set_default, 
                        is_param_set_best, 
                        is_param_set_suggested, 
                        is_manually_overriden_parameter, 
                        job_execution_id, 
                        execution_state, 
                        fitness, 
                        fitness_type, 
                        resource_usage, 
                        execution_time, 
                        input_size_in_bytes 
            ) 
SELECT     je.job_definition_id, 
           jsps.id, 
           jsps.tuning_algorithm_id, 
           tjeps.tuning_enabled, 
           jsps.is_param_set_default, 
           jsps.is_param_set_best, 
           jsps.is_param_set_suggested, 
           jsps.is_manually_overriden_parameter, 
           je.id, 
           je.execution_state, 
           jsps.fitness, ( 
           CASE 
                      WHEN ( tuning_algorithm_id = 1 
                             OR tuning_algorithm_id = 3 ) THEN "RU_PER_GB" 
                      ELSE "HEURISTIC_SCORE" 
           end) AS fitness_type, 
           je.resource_usage, 
           je.execution_time, 
           je.input_size_in_bytes 
FROM       tuning_job_execution_param_set tjeps 
INNER JOIN job_execution je 
ON         tjeps.job_execution_id=je.id 
INNER JOIN job_suggested_param_set jsps 
ON         tjeps.job_suggested_param_set_id = jsps.id 
WHERE      je.execution_state IN ('SUCCEEDED' , 'FAILED') 
AND        je.id>%s 
AND        tjeps.tuning_enabled=1 
ON duplicate KEY 
UPDATE job_definition_id=je.job_definition_id, 
       job_suggested_param_set_id=jsps.id, 
       tuning_algorithm_id=jsps.tuning_algorithm_id, 
       tuning_enabled=tjeps.tuning_enabled, 
       is_param_set_default=jsps.is_param_set_default, 
       is_param_set_best=jsps.is_param_set_best, 
       is_param_set_suggested=jsps.is_param_set_suggested, 
       is_manually_overriden_parameter=jsps.is_manually_overriden_parameter, 
       job_execution_id=je.id, 
       execution_state=je.execution_state, 
       fitness=jsps.fitness, 
       fitness_type=VALUES(fitness_type), 
       resource_usage=je.resource_usage, 
       execution_time=je.execution_time, 
       input_size_in_bytes=je.input_size_in_bytes"""


get_default_param_job_execution_performance_query="""SELECT jp.job_definition_id,
       jp.job_suggested_param_set_id,
       tuning_algorithm_id,
       tuning_enabled,
       is_param_set_default,
       is_param_set_best,
       is_param_set_suggested,
       is_manually_overriden_parameter,
       jp.job_execution_id,
       execution_state,
       fitness, 
	   fitness_type, 
	   resource_usage, 
	   execution_time, 
       input_size_in_bytes
FROM job_execution_performance jp
INNER JOIN
  (SELECT jep.job_definition_id,
          max(job_execution_id) AS job_execution_id
   FROM job_execution_performance jep
   INNER JOIN tuning_job_definition tjd ON jep.job_definition_id=tjd.job_definition_id
   WHERE tjd.auto_apply=1
     AND is_param_set_suggested=0
     AND execution_state="SUCCEEDED"
   GROUP BY job_definition_id) ids ON jp.job_execution_id=ids.job_execution_id
ORDER BY job_definition_id"""


get_best_param_job_execution_performance_query="""SELECT jp.job_definition_id,
       jp.job_suggested_param_set_id,
       tuning_algorithm_id,
       tuning_enabled,
       is_param_set_default,
       is_param_set_best,
       is_param_set_suggested,
       is_manually_overriden_parameter,
       jp.job_execution_id,
       execution_state,
       fitness, 
	   fitness_type, 
	   resource_usage, 
	   execution_time, 
       input_size_in_bytes
FROM job_execution_performance jp
INNER JOIN
  (SELECT jep.job_definition_id,
          max(job_execution_id) AS job_execution_id
   FROM job_execution_performance jep
   INNER JOIN tuning_job_definition tjd ON jep.job_definition_id=tjd.job_definition_id
   WHERE tjd.auto_apply=1
     AND is_param_set_suggested=1
     AND is_param_set_best=1
     AND execution_state="SUCCEEDED"
   GROUP BY job_definition_id) ids ON jp.job_execution_id=ids.job_execution_id
ORDER BY job_definition_id"""

update_job_aggregate_performance_query="""INSERT INTO job_aggregate_performance (job_definition_id, tuning_algorithm_id, default_job_suggested_param_set_id, best_job_suggested_param_set_id, default_job_execution_id, best_job_execution_id, default_param_fitness, best_param_fitness, fitness_type, default_param_resource_usage, best_param_resource_usage, default_param_execution_time, best_param_execution_time, default_param_ru_per_gb_input, best_param_ru_per_gb_input)
VALUES (%s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s,
        %s) ON duplicate KEY
UPDATE job_definition_id =
VALUES(job_definition_id), tuning_algorithm_id =
VALUES(tuning_algorithm_id), default_job_suggested_param_set_id =
VALUES(default_job_suggested_param_set_id), best_job_suggested_param_set_id =
VALUES(best_job_suggested_param_set_id), default_job_execution_id =
VALUES(default_job_execution_id), best_job_execution_id =
VALUES(best_job_execution_id), default_param_fitness =
VALUES(default_param_fitness), best_param_fitness =
VALUES(best_param_fitness), fitness_type =
VALUES(fitness_type), default_param_resource_usage =
VALUES(default_param_resource_usage), best_param_resource_usage =
VALUES(best_param_resource_usage), default_param_execution_time =
VALUES(default_param_execution_time), best_param_execution_time =
VALUES(best_param_execution_time), default_param_ru_per_gb_input =
VALUES(default_param_ru_per_gb_input), best_param_ru_per_gb_input =
VALUES(best_param_ru_per_gb_input)"""

db_config = None
conn = None

logger=logging.getLogger() 
logHandler = handlers.RotatingFileHandler('tuning_performance_reports.log', maxBytes=1073741824, backupCount=10)
formatter = logging.Formatter('%(asctime)-12s [%(levelname)s] %(message)s')  
logHandler.setFormatter(formatter)
logger.setLevel(logging.INFO) 
logger.addHandler(logHandler)

logger.info("========================================================================")

db_url=os.environ['db_url']
db_name=os.environ['db_name']
db_user=os.environ['db_user']
db_password=os.environ['db_password']

def get_mysql_connection():
        return mysql.connector.connect(user=db_user, password=db_password, host=db_url, database=db_name)

#Get max execution ID of already processed execution 
def get_max_execution_id():
	cursor=conn.cursor()
	cursor.execute(get_max_execution_id_query)
	row=cursor.fetchone()
	max_job_execution_id=0
	if row is not None: 
		max_job_execution_id = row[0]
	return max_job_execution_id

# job execution performance records 
def update_job_execution_performance(max_job_execution_id): 
    try:
    	conn.autocommit = True 
    	cursor = conn.cursor()
    	args = (max_job_execution_id, )
    	logger.info("Max Job Execution ID " + str(max_job_execution_id))
    	cursor.execute(update_job_execution_performance_query, args)
    	logger.info("Number of records updated " + str(cursor.rowcount))

    except Exception as e:
    	conn.rollback()
    	logger.error(e)

    finally:
	   	cursor.close()

# get job execution performance data
def get_job_execution_performance(query):
	jobExecutionMap = {}
	cursor=conn.cursor()
	cursor.execute(query)
	row=cursor.fetchone()
	while row is not None: 
		job_definition_id = row[0]
		job_suggested_param_set_id = row[1]
		tuning_algorithm_id = row[2]
		tuning_enabled = row[3]
		is_param_set_default = row[4]
		is_param_set_best = row[5]
		is_param_set_suggested = row[6]
		is_manually_overriden_parameter = row[7]
		job_execution_id = row[8]
		execution_state = row[9]
		fitness = row[10]
		fitness_type = row[11]
		resource_usage = row[12]
		execution_time = row[13]
		input_size_in_bytes = row[14]
		job_execution_performance = JobExecutionPerformance(job_definition_id, job_suggested_param_set_id, tuning_algorithm_id, tuning_enabled, 
is_param_set_default, is_param_set_best, is_param_set_suggested, is_manually_overriden_parameter, job_execution_id, execution_state, fitness, fitness_type, resource_usage,execution_time, input_size_in_bytes)
		jobExecutionMap[job_definition_id] = job_execution_performance
		row = cursor.fetchone()
	return jobExecutionMap

#Aggregate performance data 
def update_job_aggregate_performance(default_param_job_exec_perf_map, best_param_job_exec_perf_map): 
	job_agg_perf_list=[]
	for job_definition_id, dpjep in default_param_job_exec_perf_map.items():
		if best_param_job_exec_perf_map.has_key(job_definition_id):
			bpjep = best_param_job_exec_perf_map[job_definition_id]
		else:
			bpjep = dpjep

		job_aggregate_performance =  (dpjep.job_definition_id, dpjep.tuning_algorithm_id, dpjep.job_suggested_param_set_id, bpjep.job_suggested_param_set_id, dpjep.job_execution_id, bpjep.job_execution_id, dpjep.fitness, bpjep.fitness, dpjep.fitness_type, dpjep.resource_usage, bpjep.resource_usage, 
dpjep.execution_time, bpjep.execution_time, dpjep.get_ru_per_gb_input(), bpjep.get_ru_per_gb_input())
		job_agg_perf_list.append(job_aggregate_performance)
	update_job_aggregate_performance_table(job_agg_perf_list)

# update job aggregate performance table 
def update_job_aggregate_performance_table(records_to_update): 
    try:
    	conn.autocommit = True 
    	cursor = conn.cursor()
    	cursor.executemany(update_job_aggregate_performance_query, records_to_update)

    except Exception as e:
    	conn.rollback()
    	logger.error(e)

    finally:
    	cursor.close()

class JobExecutionPerformance:
	def __init__(self, _job_definition_id, _job_suggested_param_set_id, _tuning_algorithm_id, _tuning_enabled, 
_is_param_set_default, _is_param_set_best, _is_param_set_suggested, _is_manually_overriden_parameter, _job_execution_id, _execution_state, _fitness, _fitness_type, _resource_usage, _execution_time, _input_size_in_bytes):
		self.job_definition_id = _job_definition_id
		self.job_suggested_param_set_id = _job_suggested_param_set_id
		self.tuning_algorithm_id = _tuning_algorithm_id
		self.tuning_enabled = _tuning_enabled
		self.is_param_set_default = _is_param_set_default
		self.is_param_set_best = _is_param_set_best
		self.is_param_set_suggested = _is_param_set_suggested
		self.is_manually_overriden_parameter = _is_manually_overriden_parameter 
		self.job_execution_id = _job_execution_id
		self.execution_state = _execution_state
		self.fitness = _fitness
		self.fitness_type = _fitness_type
		self.resource_usage = _resource_usage
		self.execution_time = _execution_time
		self.input_size_in_bytes = _input_size_in_bytes

	def get_ru_per_gb_input(self): 
		if self.input_size_in_bytes != 0:
			return self.resource_usage / (self.input_size_in_bytes * 1.0 / (1024 * 1024 * 1024))
		else:
			return 0

class JobAggregatePerformance: 
	def __init__(self, _job_definition_id, _tuning_algorithm_id, _default_job_suggested_param_set_id, _best_job_suggested_param_set_id, _default_job_execution_id, _best_job_execution_id, _default_param_fitness, _best_param_fitness, _fitness_type, _default_param_resource_usage, _best_param_resource_usage, 
_default_param_execution_time, _best_param_execution_time, _default_param_ru_per_gb_input, _best_param_ru_per_gb_input):
		self.job_definition_id = _job_definition_id
		self.tuning_algorithm_id = _tuning_algorithm_id
		self.default_job_suggested_param_set_id = _default_job_suggested_param_set_id
		self.best_job_suggested_param_set_id = _best_job_suggested_param_set_id
		self.default_job_execution_id = _default_job_execution_id
		self.best_job_execution_id = _best_job_execution_id
		self.default_param_fitness = _default_param_fitness
		self.best_param_fitness = _best_param_fitness
		self.fitness_type = _fitness_type
		self.default_param_resource_usage = _default_param_resource_usage
		self.best_param_resource_usage = _best_param_resource_usage
		self.default_param_execution_time = _default_param_execution_time
		self.best_param_execution_time = _best_param_execution_time
		self.default_param_ru_per_gb_input = _default_param_ru_per_gb_input
		self.best_param_ru_per_gb_input = _best_param_ru_per_gb_input


conn=get_mysql_connection()
logger.info("mysql connection established")

max_execution_id = get_max_execution_id()
update_job_execution_performance(max_execution_id)
default_param_job_exec_perf_map=get_job_execution_performance(get_default_param_job_execution_performance_query)
best_param_job_exec_perf_map=get_job_execution_performance(get_best_param_job_execution_performance_query)
update_job_aggregate_performance(default_param_job_exec_perf_map, best_param_job_exec_perf_map)

conn.close()
logger.info("========================================================================")

