package com.linkedin.drelephant.tuning;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.linkedin.drelephant.AutoTuner;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.util.ProcessUtil;
import com.linkedin.drelephant.util.Utils;


public class TuningPerformanceReportManager implements Runnable {

  private final Logger logger = Logger.getLogger(getClass());

  private static final String PYTHON27_PATH_CONF = "python.path";
  private static final String DEFAULT_PYTHON27_PATH = "python";
  private static final String DEFAULT_PERFORMANCE_REPORT_SCRIPT_PATH = "./scripts/tuning_performance_reports.py";
  private static final String PERFORMANCE_REPORT_SCRIPT_PATH = "performance.report.script";
  private static final String PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL = "performance.report.daemon.wait.interval.ms";
  private static final long DEFAULT_PERFORMANCE_REPORT_DAEMON_WAIT_INTERVAL = AutoTuner.ONE_DAY;

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
          ProcessUtil.executeScript(pythonPath + " " + tuningPerformanceReportScriptPath);
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
}
