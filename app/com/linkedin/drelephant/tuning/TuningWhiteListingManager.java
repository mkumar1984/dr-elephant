package com.linkedin.drelephant.tuning;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.linkedin.drelephant.AutoTuner;
import com.linkedin.drelephant.ElephantContext;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

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
          executeScript();
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

  public boolean executeScript() {
    List<String> error = new ArrayList<String>();
    try {
      Process p = Runtime.getRuntime().exec(pythonPath + " " + whitelistingScriptPath);
      logger.info(pythonPath + " " + whitelistingScriptPath);

      BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      String errorLine;
      while ((errorLine = errorStream.readLine()) != null) {
        error.add(errorLine);
      }
      if (error.size() != 0) {
        logger.error("Error in python script running whitelist manager: " + error.toString());
      } else {
        return true;
      }
    } catch (IOException e) {
      logger.error("Error in executeScript()", e);
    }
    return false;
  }
}
