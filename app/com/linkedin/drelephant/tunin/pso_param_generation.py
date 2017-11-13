import inspyred
from random import Random
#from restartable_pso import restartable_pso
import argparse
import time
import json
import imp

restartable_pso = imp.load_source('restartable_pso', 'restartable_pso.py')


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
    params[i] = int(round(params[i])) #if param_value_type[i] == 'int' else float(params[i])
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
    value_type = str(parameter['paramValueType'])
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
    param_value_type.append(value_type)
    param_name.append(name)
    param_default_value.append(default_value)
    param_step_size.append(step_size)
    param_value_range.append((min_value, max_value))


def initial_pop_generator(random, args):
  # print "Random: ", random
  # From the list of algo, get the index of each param param 
  #0 mapreduce.task.io.sort.factor     5 150 10  int
  #1 mapreduce.task.io.sort.mb     50  6144  100 int
  #2 mapreduce.map.sort.spill.percent    0.60  0.90  0.80  float
  #3 mapreduce.map.memory.mb       1280  8192  4096  int
  #4 mapreduce.reduce.memory.mb      1280  8192  4096  int
  #5 pig.maxCombinedSplitSize      512 512 512 int
  # sort_memory_index
  # sort_factor_index
  # spill_percent_index
  # map_memory_index
  # reduce_memory_index
  # maxCombinedSplitSize_index

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
  global itr

  if itr == 0:
      itr += 1
      return param_default_value

  init = [random.uniform(x, y) for x, y in param_value_range]

  if itr%2==1:
      init[map_memory_index] = random.uniform(0.5, 0.8) * param_default_value[map_memory_index]
      init[reduce_memory_index] = random.uniform(0.5, 0.8) * param_default_value[reduce_memory_index]

  if itr%2==0:
      init[map_memory_index] = random.uniform(1.2, 1.5) * param_default_value[map_memory_index]
      init[reduce_memory_index] = random.uniform(1.2, 1.5) * param_default_value[reduce_memory_index]

  init[sort_memory_index] = random.uniform(0.0, 0.25) * init[sort_memory_index]
  init[maxCombinedSplitSize_index] = param_default_value[maxCombinedSplitSize_index]
  itr += 1

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
        fitness.append(0)
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
    if 'candidate' in element:
      individual.candidate = element['candidate'] 
    elif '_candidate' in element:
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
#    pso.observer = inspyred.ec.observers.stats_observer
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

    str_rnd_state = '[3, [2006333710, 3375878793, 1217310381, 1296678714, 1474680376, 3093257060, 3430774542, 31173228, 1138077575, 3157896560, 2984031907, 3014055906, 4182204652, 626779973, 3108963398, 1699073281, 1133321721, 248446074, 2699634459, 665011002, 2926426789, 3205553691, 4166942209, 945092382, 3716606544, 2981490853, 4286802253, 3647686944, 2461948700, 1817570586, 3083108787, 365999368, 106889756, 3609030086, 1993303116, 74305394, 3753033409, 767536914, 352034511, 3872926335, 103410400, 854584321, 2059817337, 1422496562, 2130301034, 1354872203, 3755311370, 1336679234, 247458187, 3908489024, 515328095, 3406203559, 3579480315, 3197130662, 966897711, 2514565170, 3049712791, 3737064563, 1916450496, 3469197780, 1618325787, 2183910041, 2566066284, 1294642857, 2502866934, 51692808, 518959257, 1850926852, 2421074669, 3460024047, 3917082596, 1064926984, 4111255747, 1079660959, 3493229939, 2353208719, 348651371, 2372089128, 1508119379, 2248972842, 2953010869, 2103671702, 2766849789, 3239010778, 2114908577, 3217040904, 2218760425, 3851596256, 3632728875, 320726153, 716589477, 1687764358, 1814281217, 1859487535, 851182571, 531985642, 3084983786, 1530255152, 790774201, 2221273688, 3842965786, 1900972387, 2162611025, 2686081918, 471552289, 2590755149, 1691851355, 2459707837, 3127654987, 4266156592, 802247911, 191429288, 902755269, 2433618955, 2748738437, 2399515999, 2231438694, 2013582283, 3523245271, 2301266328, 2792278984, 1489667338, 3998525316, 1131085810, 430997633, 2029243346, 231503672, 2944806889, 136269962, 969005914, 2555678104, 3808735489, 277128589, 6100237, 1457724942, 3343978725, 2650799803, 2470423259, 4216555694, 1679289801, 4222386564, 2731799250, 1821775765, 4035227181, 286624430, 4270835915, 2976603294, 4271470426, 1160238682, 3996260476, 2307407235, 518192279, 3173207352, 1664936400, 4051943586, 1989213275, 3741307687, 2719167508, 93832594, 1990935006, 2055767500, 3168586988, 3065990061, 1017888848, 926408346, 4202584896, 3196654810, 2991194665, 3189074566, 2583881817, 154549899, 1427173624, 1448909018, 2663006015, 2963605326, 734574639, 143408721, 1680304964, 3219203530, 3337377661, 749704636, 2181876167, 1300863945, 1170280486, 604004005, 3099679137, 584027974, 2812988162, 2123880877, 320559660, 3874621530, 1533236993, 1779836143, 161717471, 1932589423, 3279330604, 2992741118, 839916736, 2658782505, 3375724950, 4154948164, 1510967126, 2646608899, 3661747082, 2512796572, 2775921918, 781124188, 1546859411, 1147332436, 2502373039, 3833428401, 3791294342, 2709067400, 2343544814, 2644874676, 726894220, 2312776837, 3779259399, 2182190936, 1796126216, 1209800677, 2934664154, 3523166061, 1399999017, 3099805097, 3901030671, 1694222621, 3702517352, 2117896891, 3345973307, 3151843777, 3948226549, 1161762195, 1710828142, 1543914942, 2874675652, 112534403, 1703782550, 1699596175, 2602080444, 2669994680, 3045947379, 1655373063, 3718577825, 903598710, 645521108, 2497727793, 2655364653, 132248792, 69418786, 1257086316, 2417115703, 1672922522, 2865452, 1933684141, 1840556053, 1441957148, 1161591285, 2564457641, 189273772, 2849688440, 1513312226, 3466487988, 3998372825, 3491353343, 568038849, 3835173758, 113959288, 4144372392, 1579493307, 343798206, 4190691289, 1027936512, 757151254, 3134746640, 1859474229, 766770115, 2156243973, 3888262278, 289516094, 1127097914, 2236503090, 59344074, 1405828348, 3270614708, 4092692883, 415728326, 3407918069, 1719461660, 4252451396, 4020796422, 3793093691, 1960390860, 4280796823, 15737907, 3144297769, 845470350, 3908459083, 3469809533, 138369462, 1042564223, 4166005354, 1848277649, 2706249199, 1135005072, 2064554808, 3838275166, 762029807, 878080971, 2385973778, 3265884057, 554625579, 2916288359, 163278082, 691923118, 2996561397, 876723552, 1929957017, 1488114312, 4039162436, 1342746450, 2969613924, 4064991291, 2335968734, 1519304122, 1238006015, 2399110669, 2053372049, 1302810829, 1992629647, 1615901719, 2423012832, 3917981486, 4134078061, 3271213212, 2530984976, 1645102057, 4229324904, 218185550, 216381964, 248225089, 1577130751, 2052605013, 945587826, 1101926207, 3911660189, 2544031924, 3819576614, 1386388241, 1931717338, 1344624860, 3375721592, 2348784983, 2436390959, 1986318186, 709706072, 96777697, 2127275986, 2671425547, 2967851620, 583043020, 1777206609, 2417223604, 1907898998, 3168043906, 1015005197, 2255317096, 2181067190, 408069345, 405947469, 2509900882, 793479978, 2268326386, 351158012, 3111718897, 879057618, 112845301, 3301342092, 3859745300, 3626257068, 2688818866, 3710125745, 3094660404, 3869001797, 1217676732, 2374464940, 759324139, 1237440310, 2898073809, 654081459, 2666072640, 877487954, 2251612475, 2360253453, 437052416, 1251537148, 2232791840, 398611039, 3876888588, 608980070, 1187264602, 2862325879, 1770602821, 3349529723, 81548554, 122842951, 2496636645, 2502981954, 1528433241, 1732264381, 1671709361, 4277988688, 3325530444, 1209451311, 3993005181, 1782970919, 90620045, 554312071, 771704648, 339303414, 2577269369, 2806419338, 87203859, 1510230264, 2641428306, 3794371329, 2393843325, 1497162782, 2003638422, 2831060271, 1383274374, 2492170451, 2017854930, 4035519143, 568253794, 3109365142, 3272869383, 2531680803, 4058769634, 782055909, 16006460, 965051711, 3309189303, 364868693, 4249151407, 3686097789, 3605007683, 1715852964, 4119369379, 2480339361, 4048047357, 3333050050, 1264096683, 1978036894, 231446373, 586581632, 3776929874, 1932306473, 801075419, 3125724828, 1613270647, 2692788573, 4254388748, 3570710450, 1096217087, 2516286867, 2649198562, 1526956144, 3282293204, 1074265960, 3892507283, 381927756, 817855219, 2404011550, 3365345827, 1789103736, 1972438749, 141152300, 496749934, 2313761704, 3579461083, 4048977426, 1621813760, 1751797742, 3993599179, 1411743606, 2957149327, 1512202992, 300808082, 1234008084, 1273623057, 3058418058, 913493368, 2024410639, 551446266, 2962372349, 662444119, 3100163615, 2112057036, 1088612848, 1775444030, 1953549682, 1727423817, 885684949, 2509389322, 429268531, 1584778044, 1503435378, 1644495563, 993765127, 3475022055, 1841722123, 2423068378, 3562224111, 1154013944, 178643664, 1612191677, 2114652802, 3365595907, 885265735, 1735520415, 1192434339, 1606055423, 2268984811, 1142614498, 188292843, 3796259948, 310985679, 2416569133, 2419154640, 3723568296, 4127308880, 1020231322, 565931564, 1180171804, 606917199, 1330736273, 1778605610, 1856102254, 4060319095, 1457554090, 3854378314, 2677959103, 336553292, 496566722, 2178256302, 2469212592, 831898899, 424708628, 1578782554, 1556294355, 1266730731, 2884961574, 2974273943, 2476821177, 1338768883, 1478659900, 4032741091, 758927011, 167210990, 1071888556, 502593904, 3028919841, 2265895668, 1769756652, 3935168724, 1675728945, 755829706, 4198833381, 2778308710, 3666944732, 2981576348, 3534512472, 3671058081, 2903649470, 964018872, 987121147, 2316607938, 3983453759, 2253676704, 3915026214, 1036673600, 3905347714, 2288987071, 845885462, 1506476605, 1839751872, 2529387642, 2704478245, 123526532, 2371961571, 2974011216, 3842173724, 2971310424, 2570602663, 53099931, 800006531, 521163701, 2428936828, 1153961250, 869787718, 1920737648, 3708698482, 659279983, 1684014343, 3133332729, 2334902010, 1952819942, 992866458, 890274628, 3080117568, 1981769511, 123202574, 497299470, 3465291894, 3423110562, 3237494590, 1261933694, 2011380542, 4055241721, 930826375, 470333403, 1462052406, 4276006823, 2], null]'
    # str_rnd_state = tuning_state['rnd_state']
    json_rnd_state = json.loads(str_rnd_state)
    json_rnd_state[1] = tuple(json_rnd_state[1])
    rnd_state = tuple(json_rnd_state)

    prng.setstate(rnd_state)

    pso = restartable_pso.restartable_pso(prng, _archive=archive,
                         _previous_population=prev_population)
    #pso.observer = inspyred.ec.observers.stats_observer
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
