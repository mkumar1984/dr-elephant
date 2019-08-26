import mysql.connector
import json
import re
import smtplib
import logging
import logging.handlers as handlers
import sys


if len (sys.argv) != 2 :
    print "Usage: python azkaban_job_whitelisting_blacklisting.py config_file_name "
    sys.exit (1)
config_file_name = sys.argv[1]

get_all_tuning_auto_apply_azkaban_rules_query="select rule_type, project_name, flow_name_expr, job_name_expr, job_type from tuning_auto_apply_azkaban_rules"
get_all_jobs_for_a_project_query = "select jd.id, job_def_id, tjd.auto_apply, ta.job_type from job_definition jd inner join tuning_job_definition tjd on jd.id=tjd.job_definition_id inner join tuning_algorithm ta on tjd.tuning_algorithm_id=ta.id where job_def_id rlike %s"
update_auto_apply_flag_query="""update tuning_job_definition set auto_apply=%s, updated_ts=current_timestamp where job_definition_id=%s"""
db_config = None
conn = None
updated_job_definition_ids = {}
updated_job_definition_ids[0]=[]
updated_job_definition_ids[1]=[]
BATCH_SIZE = 100

logger=logging.getLogger() 
logHandler = handlers.RotatingFileHandler('azkaban_job_whitelist.log', maxBytes=1073741824, backupCount=10)
formatter = logging.Formatter('%(asctime)-12s [%(levelname)s] %(message)s')  
logHandler.setFormatter(formatter)
logger.setLevel(logging.INFO) 
logger.addHandler(logHandler)

logger.info("========================================================================")

def get_mysql_connection():
	db_config = json.load(open(config_file_name))
	logger.info(db_config)
	return mysql.connector.connect(**db_config)

def get_project_name_expr(project_name):
	return "project=" + project_name + "&"

def get_all_jobs_for_a_project(project_name):
	jobList = []
	cursor=conn.cursor(dictionary=True)
	args = (get_project_name_expr(project_name), )
	cursor.execute(get_all_jobs_for_a_project_query, args)
	row=cursor.fetchone()
	while row is not None: 
		job_definition_id = row["id"]
		job_def_id = row["job_def_id"]
		auto_apply = row["auto_apply"]
		job_type = row["job_type"]
		job = Job(job_definition_id, job_def_id, auto_apply, job_type)
		jobList.append(job)
		row = cursor.fetchone()
	return jobList

def get_all_tuning_auto_apply_azkaban_rules():
	ruleList = {}
	projectRuleList = None
	cursor=conn.cursor(dictionary=True)
	cursor.execute(get_all_tuning_auto_apply_azkaban_rules_query)
	row=cursor.fetchone()
	while row is not None: 
		rule_type = row["rule_type"]
		project_name = row["project_name"]
		flow_name_expr = row["flow_name_expr"]
		job_name_expr = row["job_name_expr"]
		job_type = row["job_type"]
		rule=Rule(rule_type, project_name, flow_name_expr, job_name_expr, job_type)
		if(ruleList.has_key(project_name)):
			projectRuleList=ruleList[project_name]
		else : 
			projectRuleList=[]
			ruleList[project_name] = projectRuleList
		projectRuleList.append(rule)
		row = cursor.fetchone()
	return ruleList

def update_auto_apply_flag(records_to_update): 
    try:
    	conn.autocommit = True 
    	cursor = conn.cursor()
    	cursor.executemany(update_auto_apply_flag_query, records_to_update)

    except Exception as e:
    	conn.rollback()
    	logger.error(e)

    finally:
    	cursor.close()

def update_auto_apply_flag_for_jobs(jobs):
	records_to_update = []
	for job in jobs : 
		if job.auto_apply!=job.new_auto_appply:
			if len(records_to_update) >= BATCH_SIZE : 
				update_auto_apply_flag(records_to_update)
				records_to_update = []
			batch_element = (job.new_auto_appply, job.job_definition_id)
			updated_job_definition_ids[job.new_auto_appply].append(job.job_definition_id)
			records_to_update.append(batch_element)

	if len(records_to_update)>0 : 
		update_auto_apply_flag(records_to_update)

class Job:
	def __init__(self, _job_definition_id, _job_def_id, _auto_apply, _job_type):
		self.job_definition_id = _job_definition_id
		self.job_def_id = _job_def_id
		self.auto_apply = _auto_apply
		self.job_type = _job_type

	def set_new_auto_apply_value(self, auto_apply):
		self.new_auto_appply=auto_apply

	def apply_rules(self, rules):
		auto_apply=False
		for rule in rules :
			if self.job_type == rule.job_type : 
				pattern = self.get_flow_job_expr(rule)
				if re.match(pattern, self.job_def_id) :
					if rule.rule_type=="WHITELIST" : 
						auto_apply=True 
					elif rule.rule_type=="BLACKLIST" : 
						auto_apply=False
						break
		return auto_apply

	def get_flow_job_expr(self, rule):
		pattern=".*flow=" + rule.flow_name_expr + "&job=" + rule.job_name_expr + "$" 
		return pattern

class Rule:
	def __init__(self, _rule_type, _project_name, _flow_name_expr, _job_name_expr, _job_type):
		self.rule_type = _rule_type
		self.project_name = _project_name
		self.flow_name_expr = _flow_name_expr
		self.job_name_expr = _job_name_expr
		self.job_type = _job_type

	def print_rule(self):
		logger.info("Rule Type: " + self.rule_type + ", Project Name: " + self.project_name + ", Flow Name Expr: " + self.flow_name_expr + ", Job Name Expr: " + self.job_name_expr + ", Job Type: " + self.job_type)


def print_rules(rules):
	for rule in rules:
		rule.print_rule()

conn=get_mysql_connection()
logger.info("mysql connection established")

rulesMap = get_all_tuning_auto_apply_azkaban_rules()
for project_name, rules in rulesMap.items():
	jobs=get_all_jobs_for_a_project(project_name)
	print_rules(rules)
	for job in jobs:
		auto_apply=job.apply_rules(rules)
		job.set_new_auto_apply_value(auto_apply)
		logger.debug("Auto Apply " + str(auto_apply) + " for job " + str(job.job_definition_id) + " for job ID " + str(job.job_def_id))
	update_auto_apply_flag_for_jobs(jobs)

conn.close()
logger.info("Following job definition IDs disabled for auto tuning ")
logger.info(updated_job_definition_ids[0])
logger.info("")
logger.info("Following job definition IDs enabled for auto tuning ")
logger.info(updated_job_definition_ids[1])
logger.info("========================================================================")
