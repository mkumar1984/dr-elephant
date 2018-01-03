import inspyred
from random import Random
import argparse
import time
import json
import imp
import os

restartable_pso = imp.load_source('restartable_pso', '/Users/aragrawa/development/dr-elephant/app/com/linkedin/drelephant/tunin/pso/restartable_pso.py')


param_value_type = []
param_value_range = []
param_step_size = []
param_default_value = []
param_name = []



itr = 0;



# Todo
def fix_data_type(params):
  params_as_json = {}

  for i in xrange(0, len(params)):
    params[i] = int(round(params[i]))
    params[i] *= param_step_size[i]
    params_as_json[param_name[i]] = int(params[i]) if param_value_type[
                                i] == 'int' \
      else float(params[i])
    if param_name[i] == 'pig.maxCombinedSplitSize':
      params_as_json['pig.maxCombinedSplitSize'] = int(
          params[i]) * 1024 * 1024
      if not 'mapreduce.input.fileinputformat.split.maxsize' in \
          param_name:
        params_as_json[
          'mapreduce.input.fileinputformat.split.maxsize'] = int(
            params[i]) * 1024 * 1024

    elif param_name[i] == 'mapreduce.map.memory.mb':
      if not 'mapreduce.map.java.opts' in param_name:
        params_as_json['mapreduce.map.java.opts'] = '-Xmx%dm' % (
          0.75 * int(params[i]))

    elif param_name[i] == 'mapreduce.reduce.memory.mb':
      if not 'mapreduce.reduce.java.opts' in param_name:
        params_as_json['mapreduce.reduce.java.opts'] = '-Xmx%dm' % (
          0.75 * int(params[i]))
  return params_as_json


def initialize_params(parameters_to_tune):
  for parameter in parameters_to_tune:
    value_type = 'DOUBLE'
    name = str(parameter['paramName'])

    if value_type == 'INT':
      step_size = int(parameter['stepSize'])
      default_value = int(parameter['defaultValue'])
      max_value = int(parameter['maxValue'])
      min_value = int(parameter['minValue'])
    else:
      step_size = float(parameter['stepSize'])
      default_value = float(parameter['defaultValue'])
      max_value = float(parameter['maxValue'])
      min_value = float(parameter['minValue'])
    param_value_type.append('DOUBLE')
    param_name.append(name)
    param_default_value.append(default_value)
    param_step_size.append(step_size)
    param_value_range.append((min_value, max_value))


def initial_pop_generator(random, args):
  # From the list of algo, get the index of each param param
  #0 mapreduce.task.io.sort.factor     5 150 10  int
  #1 mapreduce.task.io.sort.mb     50  6144  100 int
  #2 mapreduce.map.sort.spill.percent    0.60  0.90  0.80  float
  #3 mapreduce.map.memory.mb       1280  8192  4096  int
  #4 mapreduce.reduce.memory.mb      1280  8192  4096  int
  #5 pig.maxCombinedSplitSize      512 512 512 int
  for i in range(0, len(param_name)):
    if param_name[i] == 'mapreduce.task.io.sort.factor':
      sort_factor_index = i

    elif param_name[i] == 'mapreduce.task.io.sort.mb':
      sort_memory_index = i
    elif param_name[i] == 'mapreduce.map.sort.spill.percent':
      spill_percent_index = i
    elif param_name[i] == 'mapreduce.map.memory.mb':
      map_memory_index = i
    elif param_name[i] == 'mapreduce.reduce.memory.mb':
      reduce_memory_index = i
    elif param_name[i] == 'pig.maxCombinedSplitSize':
      maxCombinedSplitSize_index = i
    # elif param_name[i] == 'mapreduce.map.java.opts':
    #   map_java_opts_index = i
    # elif param_name[i] == 'mapreduce.reduce.java.opts':
    #   reduce_java_opts_index = i
    # elif param_name[i] == 'mapreduce.input.fileinputformat.split.maxsize':
    #   fileinput_format_split_size_index = i
  global itr

  if itr == 0:
    itr += 1
    init = param_default_value
    #return param_default_value

  else:
    init = [random.uniform(x, y) for x, y in param_value_range]
    if itr%2==1:
        init[map_memory_index] = random.uniform(0.5, 0.8) * param_default_value[map_memory_index]
        init[reduce_memory_index] = random.uniform(0.5, 0.8) * param_default_value[reduce_memory_index]

    if itr%2==0:
        init[map_memory_index] = random.uniform(1.2, 1.5) * param_default_value[map_memory_index]
        init[reduce_memory_index] = random.uniform(1.2, 1.5) * param_default_value[reduce_memory_index]

    init[sort_memory_index] = random.uniform(0.0, 0.25) * init[map_memory_index]
    init[maxCombinedSplitSize_index] = param_default_value[maxCombinedSplitSize_index]

    # init[map_java_opts_index] = 0.75 * init[map_memory_index]
    # init[reduce_java_opts_index] = 0.75 * init[reduce_memory_index]
    # init[fileinput_format_split_size_index] = init[maxCombinedSplitSize_index]
    itr += 1

  for i in range(0,len(param_name)):
    (min_val, max_val) = param_value_range[i]
    init[i] = max(min_val, min(max_val, init[i]))

  return init

def get_lower_bound():
    return [x for x, y in param_value_range]


def get_upper_bound():
    return [y for x, y in param_value_range]


def bounder(candidate, args):
  lower_bound = get_lower_bound()
  upper_bound = get_upper_bound()
  bounded_candidate = candidate
  for i, (c, lo, hi) in enumerate(zip(candidate, lower_bound,
                                      upper_bound)):
      bounded_candidate[i] = max(min(c, hi), lo)
  return bounded_candidate


def dummy_fitness_evaluator(candidates, args):
    fitness = []
    for cs in candidates:
        fitness.append(float("inf"))
    return fitness


def save_tuning_state(pso, prng, population):
    data = {}
    archive = []
    prev_population = []
    current_population = []
    rnd_state = json.dumps(prng.getstate())

    for individual in pso.archive:
      archive.append(individual.__dict__)
    for individual in pso._previous_population:
      prev_population.append(individual.__dict__)
    for individual in population:
      current_population.append(individual.__dict__)

    data['archive'] = archive
    data['prev_population'] = prev_population
    data['current_population'] = current_population # todo: send 2 or 3?
    data['rnd_state'] = rnd_state
    data_dump = json.dumps(data)
    return data_dump

def json_to_individual_object(json_list):
  individuals = []

  for element in json_list:
    individual = inspyred.ec.ec.Individual()
    # if 'candidate' in element:
    #   individual.candidate = element['candidate']
    # elif '_candidate' in element:
    individual.candidate = element['_candidate']
    individual.maximize = element['maximize']
    individual.fitness = element['fitness']
    individual.birthday = element['birthdate']
    individuals.append(individual)
  return individuals



def main(json_tuning_state, display=False):
  tuning_state = json.loads(json_tuning_state)
  prng = Random()
  args = {}

  if 'archive' not in tuning_state:
    prng.seed(time.time())
    pso = restartable_pso.restartable_pso(prng)
    pso.observer = inspyred.ec.observers.default_observer
    pso.terminator = inspyred.ec.terminators.evaluation_termination
    pso.topology = inspyred.swarm.topologies.ring_topology
    population = pso.evolve(generator=initial_pop_generator, evaluator=dummy_fitness_evaluator, pop_size=3,
                          bounder=bounder,
                          maximize=False, max_evaluations=3, **args)

    tuning_state = save_tuning_state(pso, prng, population)
    print tuning_state

  else:
    archive = json_to_individual_object(tuning_state['archive'])
    prev_population = json_to_individual_object(tuning_state['prev_population'])
    initial_population = json_to_individual_object(tuning_state['current_population'])

    str_rnd_state = tuning_state['rnd_state']
    json_rnd_state = json.loads(str_rnd_state)
    json_rnd_state[1] = tuple(json_rnd_state[1])
    rnd_state = tuple(json_rnd_state)

    prng.setstate(rnd_state)

    pso = restartable_pso.restartable_pso(prng, _archive=archive,
                         _previous_population=prev_population)
    pso.observer = inspyred.ec.observers.default_observer
    pso.terminator = inspyred.ec.terminators.evaluation_termination
    pso.topology = inspyred.swarm.topologies.ring_topology

    population = pso.evolve(seeds=[cs.candidate for cs in initial_population], initial_fit = [cs.fitness for cs in initial_population],
                          generator=None, evaluator=dummy_fitness_evaluator, pop_size=3,
                          bounder=bounder,
                          maximize=False, max_evaluations=6, **args)
    tuning_state = save_tuning_state(pso, prng, population)
    print tuning_state

if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  parser.add_argument('json_tuning_state', help='Saved tuning state object')
  parser.add_argument('parameters_to_tune')
  args = parser.parse_args()
  json_tuning_state = args.json_tuning_state
  parameters_to_tune = args.parameters_to_tune
  parameters_to_tune = json.loads(parameters_to_tune)
  initialize_params(parameters_to_tune)
  main(json_tuning_state)