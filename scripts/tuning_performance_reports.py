import mysql.connector
import json
import re
import smtplib
import logging
import logging.handlers as handlers
import sys
import csv


job_definition_ids = "(100141, 100151, 106638,106664,106933,107230,107535,112720,112721,113359,116495,117326,117339,117342,117343,117344,117345,117351,117373,117374,117387,117434,117446,117941,117975,117998,118011,118052,118405,118607,120929,124253,125524,126922,128894,128948,129478,123630,119250,119091,119373,119252,119236,119271,119239,119314,119218,119278,119254,119289,119253,119315,119277,119255,119257,119248,119249,119238,119244,119233,119279,119241, 132773,118034,106435,107533,107538,107740,119245,107744,107092,107540,121855,106601,106602,133663,118017,117954,106559,132513,106809,107206,106811,118800,134349,107062,106603,119243,118007,106686,106687,106729,106605,126552,106730,119131,117331,124494)"

if len (sys.argv) != 2 :
    print "Usage: python tuning_performance_reports.py config_file_name "
    sys.exit (1)
config_file_name = sys.argv[1]

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
AND		   je.input_size_in_bytes>1
AND		   je.job_definition_id in  """ + job_definition_ids +  """on duplicate KEY 
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

get_performance_report_data_query="""select job_definition_id, default_param_fitness as default_parameter_hueristics_score, best_param_fitness as best_parameter_hueristics_score, round(default_param_ru_per_gb_input, 6) as default_param_ru_per_gb_input, round(best_param_ru_per_gb_input, 6) as best_param_ru_per_gb_input,  job_def_id 
from job_aggregate_performance jap inner join job_definition jd on jap.job_definition_id = jd.id
where job_definition_id in """ + job_definition_ids

db_config = None
conn = None

logger=logging.getLogger() 
logHandler = handlers.RotatingFileHandler('tuning_performance_reports.log', maxBytes=1073741824, backupCount=10)
formatter = logging.Formatter('%(asctime)-12s [%(levelname)s] %(message)s')  
logHandler.setFormatter(formatter)
logger.setLevel(logging.INFO) 
logger.addHandler(logHandler)

logger.info("========================================================================")

def get_mysql_connection():
	db_config = json.load(open(config_file_name))
	logger.info(db_config)
	return mysql.connector.connect(**db_config)

def get_max_execution_id():
	cursor=conn.cursor(dictionary=True)
	cursor.execute(get_max_execution_id_query)
	row=cursor.fetchone()
	max_job_execution_id=0
	if row is not None: 
		max_job_execution_id = row["max_job_execution_id"]
	return max_job_execution_id

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

def get_job_execution_performance(query):
	jobExecutionMap = {}
	cursor=conn.cursor(dictionary=True)
	cursor.execute(query)
	row=cursor.fetchone()
	while row is not None: 
		job_definition_id = row["job_definition_id"]
		job_suggested_param_set_id = row["job_suggested_param_set_id"]
		tuning_algorithm_id = row["tuning_algorithm_id"]
		tuning_enabled = row["tuning_enabled"]
		is_param_set_default = row["is_param_set_default"]
		is_param_set_best = row["is_param_set_best"]
		is_param_set_suggested = row["is_param_set_suggested"]
		is_manually_overriden_parameter = row["is_manually_overriden_parameter"]
		job_execution_id = row["job_execution_id"]
		execution_state = row["execution_state"]
		fitness = row["fitness"]
		fitness_type = row["fitness_type"]
		resource_usage = row["resource_usage"]
		execution_time = row["execution_time"]
		input_size_in_bytes = row["input_size_in_bytes"]
		job_execution_performance = JobExecutionPerformance(job_definition_id, job_suggested_param_set_id, tuning_algorithm_id, tuning_enabled, 
is_param_set_default, is_param_set_best, is_param_set_suggested, is_manually_overriden_parameter, job_execution_id, execution_state, fitness, fitness_type, resource_usage,execution_time, input_size_in_bytes)
		jobExecutionMap[job_definition_id] = job_execution_performance
		row = cursor.fetchone()
	return jobExecutionMap

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


def get_performance_report_data():
	performance_report_records = []
	cursor=conn.cursor(dictionary=True)
	cursor.execute(get_performance_report_data_query)
	row=cursor.fetchone()
	while row is not None: 
		job_definition_id = row["job_definition_id"]
		default_parameter_hueristics_score = row["default_parameter_hueristics_score"]
		best_parameter_hueristics_score = row["best_parameter_hueristics_score"]
		heurtisc_score_improvement = best_parameter_hueristics_score - default_parameter_hueristics_score
		default_param_ru_per_gb_input = row["default_param_ru_per_gb_input"]
		best_param_ru_per_gb_input = row["best_param_ru_per_gb_input"]
		if default_param_ru_per_gb_input!=0:
			ru_improvement = round((default_param_ru_per_gb_input - best_param_ru_per_gb_input) * 100 / default_param_ru_per_gb_input, 2)
		else:
			ru_improvement = 0
		job_def_id = row["job_def_id"]
		performance_report_record = (job_definition_id, default_parameter_hueristics_score, best_parameter_hueristics_score, heurtisc_score_improvement, default_param_ru_per_gb_input, best_param_ru_per_gb_input, ru_improvement, job_def_id)
		performance_report_records.append(performance_report_record)
		row = cursor.fetchone()
	return performance_report_records


def write_performance_report_csv(): 
	header=("Tuning Job Definition ID", "Default Parameter Heuristics Score", "Best Parameter Heuristics Score", "Heuristics Score Improvement", "Default Parameter Resource Usage Per GB Input", "Best Parameter Resource Usage Per GB Input", "Resource Usage Improvement",  "Azkaban Job Definition ID")
	performance_report_records=get_performance_report_data()
	performance_report_records.insert(0, header)
	with open('performance_report.csv', 'w') as csvFile:
		writer = csv.writer(csvFile)
		writer.writerows(performance_report_records)
	csvFile.close()

conn=get_mysql_connection()
logger.info("mysql connection established")

max_execution_id = get_max_execution_id()
update_job_execution_performance(max_execution_id)
default_param_job_exec_perf_map=get_job_execution_performance(get_default_param_job_execution_performance_query)
best_param_job_exec_perf_map=get_job_execution_performance(get_best_param_job_execution_performance_query)
update_job_aggregate_performance(default_param_job_exec_perf_map, best_param_job_exec_perf_map)
write_performance_report_csv()
conn.close()
logger.info("========================================================================")

