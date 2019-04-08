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


public class TuningPerformanceReportManager implements Runnable {

  private final Logger logger = Logger.getLogger(getClass());

  private static final String PYTHON27_PATH_CONF = "python27.path";
  private static final String DEFAULT_PYTHON27_PATH = "python2.7";
  private static final String DEFAULT_PERFORMANCE_REPORT_SCRIPT_PATH = "./scripts/tuning_performance_reports.py";
  private static final String PERFORMANCE_REPORT_SCRIPT_PATH = "performance.report.script";
  public static final String PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL = "performance.report.daemon.wait.interval.ms";
  public static final long DEFAULT_PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL = AutoTuner.ONE_DAY;

  private String pythonPath = null;
  private String tuningPerformanceReportScriptPath = null;
  private Long tuningPerformanceReportInterval;

  public TuningPerformanceReportManager() {
    Configuration configuration = ElephantContext.instance().getAutoTuningConf();
    pythonPath = configuration.get(PYTHON27_PATH_CONF, DEFAULT_PYTHON27_PATH);
    tuningPerformanceReportScriptPath =
        configuration.get(PERFORMANCE_REPORT_SCRIPT_PATH, DEFAULT_PERFORMANCE_REPORT_SCRIPT_PATH);
    tuningPerformanceReportInterval =
        Utils.getNonNegativeLong(configuration, PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL,
            DEFAULT_PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL);
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          logger.info("Start: Executing TuningPerformanceReportManager ");
          executeScript();
          logger.info("End: Executing TuningPerformanceReportManager ");
        } catch (Exception e) {
          logger.error("Error in TuningPerformanceReportManager thread ", e);
        }
        Thread.sleep(tuningPerformanceReportInterval);
      }
    } catch (Exception e) {
      logger.error("Error in TuningPerformanceReportManager thread ", e);
    }
  }

  public boolean executeScript() {
    List<String> error = new ArrayList<String>();
    try {
      Process p = Runtime.getRuntime().exec(pythonPath + " " + tuningPerformanceReportScriptPath);
      logger.info(pythonPath + " " + tuningPerformanceReportScriptPath);

      BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      String errorLine;
      while ((errorLine = errorStream.readLine()) != null) {
        error.add(errorLine);
      }
      if (error.size() != 0) {
        logger.error("Error in python script running TuningPerformanceReportManager: " + error.toString());
      } else {
        return true;
      }
    } catch (IOException e) {
      logger.error("Error in executeScript()", e);
    }
    return false;
  }
}
