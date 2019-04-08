package com.linkedin.drelephant.tuning;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.linkedin.drelephant.AutoTuner;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.util.ProcessUtil;
import com.linkedin.drelephant.util.Utils;


public class TuningWhiteListingManager implements Runnable {

  private final Logger logger = Logger.getLogger(getClass());

  private static final String PYTHON_PATH_CONF = "python.path";
  private static final String DEFAULT_PYTHON_PATH = "python";
  private static final String DEFAULT_WHITELISTING_SCRIPT_PATH = "./scripts/azkaban_job_whitelisting_blacklisting.py";
  private static final String WHITELISTING_SCRIPT_PATH = "tuning.whitelisting.script";
  private static final String WHITELISTING_DAEMON_WAIT_INTERVAL = "whitelisting.daemon.wait.interval.ms";
  private static final long DEFAULT_WHITELISTING_DAEMON_WAIT_INTERVAL = AutoTuner.ONE_DAY;

  private String pythonPath = null;
  private String whitelistingScriptPath = null;
  private Long whiteListingInterval;

  public TuningWhiteListingManager() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    pythonPath = configuration.get(PYTHON_PATH_CONF, DEFAULT_PYTHON_PATH);
    whitelistingScriptPath = configuration.get(WHITELISTING_SCRIPT_PATH, DEFAULT_WHITELISTING_SCRIPT_PATH);
    whiteListingInterval =
        Utils.getNonNegativeLong(configuration, WHITELISTING_DAEMON_WAIT_INTERVAL,
            DEFAULT_WHITELISTING_DAEMON_WAIT_INTERVAL);
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          logger.info("Start: Executing TuningWhitelistingManager ");
          ProcessUtil.executeScript(pythonPath + " " + whitelistingScriptPath);
          logger.info("End: Executing TuningWhitelistingManager ");
        } catch (Exception e) {
          logger.error("Error in TuningWhiteListingManager thread ", e);
        }
        Thread.sleep(whiteListingInterval);
      }
    } catch (Exception e) {
      logger.error("Error in TuningWhiteListingManager thread ", e);
    }
  }

}
