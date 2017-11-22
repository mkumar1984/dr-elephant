package com.linkedin.drelephant;

import com.linkedin.drelephant.configurations.tunin.TuninAlgorithmConfiguration;
import com.linkedin.drelephant.configurations.tunin.TuninAlgorithmConfigurationData;
import com.linkedin.drelephant.tunin.FitnessComputeUtil;
import com.linkedin.drelephant.tunin.JobCompleteDetector;
import com.linkedin.drelephant.tunin.PSOParamGenerator;
import com.linkedin.drelephant.tunin.ParamGenerator;
import com.linkedin.drelephant.util.Utils;

import models.JobExecution;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class AutoTuner implements Runnable{

    private static final Logger logger = Logger.getLogger(AutoTuner.class);
    private static final long METRICS_COMPUTATION_INTERVAL = 5 * 60 * 1000;
    private static final String TUNIN_ALGORITHM_CONF = "TuninAlgorithmConf.xml";
    private List<TuninAlgorithmConfigurationData> _tuninAlgorithmConfData;

//    private void loadTuninAlgorithm() {
//        Document document = Utils.loadXMLDoc(TUNIN_ALGORITHM_CONF);
//
//        _tuninAlgorithmConfData = new TuninAlgorithmConfiguration(document.getDocumentElement()).getTuninAlgorithmsConfData();
//        for (TuninAlgorithmConfigurationData data : _tuninAlgorithmConfData) {
//            try {
//                Class<?> tuninAlgortihmClass = Class.forName(data.getClassName());
//                Object instance = tuninAlgortihmClass.getConstructor(TuninAlgorithmConfigurationData.class).newInstance(data);
//                if (!(instance instanceof HadoopMetricsAggregator)) {
//                    throw new IllegalArgumentException(
//                            "Class " + tuninAlgortihmClass.getName() + " is not an implementation of " + HadoopMetricsAggregator.class.getName());
//                }
//
//                ApplicationType type = data.getAppType();
//                if (_typeToAggregator.get(type) == null) {
//                    _typeToAggregator.put(type, (HadoopMetricsAggregator) instance);
//                }
//
//                logger.info("Load Aggregator : " + data.getClassName());
//            } catch (ClassNotFoundException e) {
//                throw new RuntimeException("Could not find class " + data.getClassName(), e);
//            } catch (InstantiationException e) {
//                throw new RuntimeException("Could not instantiate class " + data.getClassName(), e);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException("Could not access constructor for class" + data.getClassName(), e);
//            } catch (RuntimeException e) {
//                throw new RuntimeException(data.getClassName() + " is not a valid Aggregator class.", e);
//            } catch (InvocationTargetException e) {
//                throw new RuntimeException("Could not invoke class " + data.getClassName(), e);
//            } catch (NoSuchMethodException e) {
//                throw new RuntimeException("Could not find constructor for class " + data.getClassName(), e);
//            }
//
//        }
//    }
    public void run(){
        /**
        * Completion detector
        * Wait for an interval
        * Compute fitness
        * Param Generator
        */

       // loadTuninAlgorithm();
        try{
          try
          {
            while(!Thread.currentThread().isInterrupted())
            {
              JobCompleteDetector jobCompleteDetector = new JobCompleteDetector();
              List<JobExecution> completedJobExecution = jobCompleteDetector.updateCompletedJobs();

              FitnessComputeUtil fitnessComputeUtil = new FitnessComputeUtil();
              List<JobExecution> fitnessComputedExecution = fitnessComputeUtil.updateFitness();

              ParamGenerator paramGenerator = new PSOParamGenerator();
              paramGenerator.getParams();
            }
          }catch(Exception e)
          {
            logger.error(e.getMessage());
            logger.error(ExceptionUtils.getStackTrace(e));
          }
          Thread.sleep(METRICS_COMPUTATION_INTERVAL);
        }catch (Exception e){
          logger.error(e.getMessage());
          logger.error(ExceptionUtils.getStackTrace(e));

            try {
              Thread.sleep(METRICS_COMPUTATION_INTERVAL);
            } catch (InterruptedException e1) {
              // TODO Auto-generated catch block
            }
        }
    }
}
