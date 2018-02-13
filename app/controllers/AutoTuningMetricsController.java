package controllers;

import static com.codahale.metrics.MetricRegistry.name;

import org.apache.log4j.Logger;

import play.Configuration;
import play.mvc.Controller;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.linkedin.drelephant.AutoTuner;


public class AutoTuningMetricsController extends Controller {
  private static MetricRegistry _metricRegistry = null;
  private static final Logger logger = Logger.getLogger(AutoTuningMetricsController.class);

  private static int _fitnessComputeWaitJobs = -1;
  private static int _baselineComputeWaitJobs = -1;
  private static int _azkabanStatusUpdateWaitJobs = -1;
  private static int _paramSetGenerateWaitJobs = -1;

  private static Meter _getCurrentRunParametersFailures;
  private static Meter _fitnessComputedJobs;
  private static Meter _successfulExecutions;
  private static Meter _failedExecutions;
  private static Meter _paramSetGenerated;
  private static Meter _baselineComputed;
  private static Meter _paramSetNotFound;
  private static Meter _newAutoTuningJob;

  private static Timer _getCurrentRunParametersTimer;

  public static void init() {

    // Metrics registries will be initialized only if enabled
    if (!Configuration.root().getBoolean("metrics", false)) {
      logger.debug("Metrics not enabled in the conf file.");
      return;
    }

    // Metrics & healthcheck registries will be initialized only once
    if (_metricRegistry != null) {
      logger.debug("Metric registries already initialized.");
      return;
    }

    _metricRegistry = new MetricRegistry();

    String className = AutoTuner.class.getSimpleName();

    //API timer and failed counts
    _getCurrentRunParametersTimer = _metricRegistry.timer(name(Application.class, "getCurrentRunParametersResponses"));
    _getCurrentRunParametersFailures =
        _metricRegistry.meter(name(Application.class, "getCurrentRunParametersFailures", "count"));

    //Daemon counters
    _fitnessComputedJobs = _metricRegistry.meter(name(className, "fitnessComputedJobs", "count"));
    _successfulExecutions = _metricRegistry.meter(name(className, "successfulJobs", "count"));
    _failedExecutions = _metricRegistry.meter(name(className, "failedJobs", "count"));
    _paramSetGenerated = _metricRegistry.meter(name(className, "paramSetGenerated", "count"));
    _baselineComputed = _metricRegistry.meter(name(className, "baselineComputed", "count"));
    _paramSetNotFound = _metricRegistry.meter(name(className, "paramSetNotFound", "count"));
    _newAutoTuningJob = _metricRegistry.meter(name(className, "newAutoTuningJob", "count"));

    _metricRegistry.register(name(className, "fitnessComputeWaitJobs", "size"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return _fitnessComputeWaitJobs;
      }
    });

    _metricRegistry.register(name(className, "baselineComputeWaitJobs", "size"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return _baselineComputeWaitJobs;
      }
    });

    _metricRegistry.register(name(className, "azkabanStatusUpdateWaitJobs", "size"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return _azkabanStatusUpdateWaitJobs;
      }
    });

    _metricRegistry.register(name(className, "paramSetGenerateWaitJobs", "size"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return _paramSetGenerateWaitJobs;
      }
    });
  }
  public static void setFitnessComputeWaitJobs(int fitnessComputeWaitJobs) {
    _fitnessComputeWaitJobs = fitnessComputeWaitJobs;
  }

  public static void setBaselineComputeWaitJobs(int baselineComputeWaitJobs) {
    _baselineComputeWaitJobs = baselineComputeWaitJobs;
  }

  public static void setAzkabanStatusUpdateWaitJobs(int azkabanStatusUpdateWaitJobs) {
    _azkabanStatusUpdateWaitJobs = azkabanStatusUpdateWaitJobs;
  }

  public static void setParamSetGenerateWaitJobs(int paramSetGenerateWaitJobs) {
    _paramSetGenerateWaitJobs = paramSetGenerateWaitJobs;
  }

  public static void markSuccessfulJobs() {
    if (_successfulExecutions != null) {
      _successfulExecutions.mark();
    }
  }
  public static void markNewAutoTuningJob() {
    if (_newAutoTuningJob != null) {
      _newAutoTuningJob.mark();
    }
  }
  public static void markParamSetNotFound() {
    if (_paramSetNotFound != null) {
      _paramSetNotFound.mark();
    }
  }

  public static void markFailedJobs() {
    if (_failedExecutions != null) {
      _failedExecutions.mark();
    }
  }

  public static void markParamSetGenerated() {
    if (_paramSetGenerated != null) {
      _paramSetGenerated.mark();
    }
  }

  public static void markFitnessComputedJobs() {
    if (_fitnessComputedJobs != null) {
      _fitnessComputedJobs.mark();
    }
  }

  public static void markBaselineComputed() {
    if (_baselineComputed != null) {
      _baselineComputed.mark();
    }
  }

  public static void markGetCurrentRunParametersFailures() {
    if (_getCurrentRunParametersFailures != null) {
      _getCurrentRunParametersFailures.mark();
    }
  }

  public static Context getCurrentRunParametersTimerContext() {
    return _getCurrentRunParametersTimer.time();
  }

}
