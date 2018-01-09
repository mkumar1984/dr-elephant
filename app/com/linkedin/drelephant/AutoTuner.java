package com.linkedin.drelephant;

import java.util.List;

import models.TuningJobExecution;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.linkedin.drelephant.tunin.AzkabanJobCompleteDetector;
import com.linkedin.drelephant.tunin.BaselineComputeUtil;
import com.linkedin.drelephant.tunin.FitnessComputeUtil;
import com.linkedin.drelephant.tunin.JobCompleteDetector;
import com.linkedin.drelephant.tunin.PSOParamGenerator;
import com.linkedin.drelephant.tunin.ParamGenerator;
import com.linkedin.drelephant.util.Utils;


public class AutoTuner implements Runnable {

  private static final Logger logger = Logger.getLogger(AutoTuner.class);
  private static final long METRICS_COMPUTATION_INTERVAL = 60 * 1000 / 5;

  public static final String AUTO_TUNING_ENABLED = "autotuning.enabled";
  public static final String AUTO_TUNING_DAEMON_WAIT_INTERVAL = "autotuning.daemon.wait.interval.ms";

  public void run() {
    /**
    * Baseline computation
    * Completion detector
    * Compute fitness
    * Param Generator
    */
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    Boolean autoTuningEnabled = configuration.getBoolean(AUTO_TUNING_ENABLED, false);
    Long interval =
        Utils.getNonNegativeLong(configuration, AUTO_TUNING_DAEMON_WAIT_INTERVAL, METRICS_COMPUTATION_INTERVAL);
    if (autoTuningEnabled) {
      try {
        try {
          while (!Thread.currentThread().isInterrupted()) {
            BaselineComputeUtil baselineComputeUtil = new BaselineComputeUtil();
            baselineComputeUtil.computeBaseline();

            JobCompleteDetector jobCompleteDetector = new AzkabanJobCompleteDetector();
            jobCompleteDetector.updateCompletedExecutions();

            FitnessComputeUtil fitnessComputeUtil = new FitnessComputeUtil();
            fitnessComputeUtil.updateFitness();

            ParamGenerator paramGenerator = new PSOParamGenerator();
            paramGenerator.getParams();

            Thread.sleep(interval);
          }

        } catch (Exception e) {
          logger.error("Error in auto tuner thread " , e);
        }

      } catch (Exception e) {
        logger.error("Error in auto tuner thread " , e);
        try {
          Thread.sleep(interval);
        } catch (InterruptedException e1) {
        }
      }
    }
  }
}
