package com.linkedin.drelephant.spark.heuristics;

public class CommonConstantsSparkHeuristic {
  public static final String EXECUTOR_SPILL="Executor spill";
  public static final String EXECUTOR_JVM_USED_MEMORY = "Executor JVM Used Memory";
  public static final String EXECUTOR_PEAK_UNIFIED_MEMORY = "Executor Peak Unified Memory";
  public static final String STAGES_WITH_FAILED_TASKS = "Stages with failed tasks";

  public static final String EXECUTOR_SPILL_FRACTION = "Fraction of executors having non zero bytes spilled";
  public static final String MAX_SPILLED_MEMORY = "Max memory spilled";
  public static final String MAX_EXECUTOR_PEAK_JVM_USED_MEMORY = "Max executor peak JVM used memory";
  public static final String MAX_PEAK_UNIFIED_MEMORY = "Max peak unified memory";
  public static final String SPARK_EXECUTOR_MEMORY = "spark.executor.memory";
  public static final String SPARK_YARN_EXECUTOR_MEMORY_OVERHEAD = "spark.yarn.executor.memoryOverhead";
  public static final String SPARK_MEMORY_FRACTION = "spark.memory.fraction";
  public static final String SPARK_MEMORY_STORAGE_FRACTION = "spark.memory.storageFraction";

  public static final String STAGES_WITH_OOM_ERRORS = "Stages with OOM errors";
  public static final String STAGES_WITH_OVERHEAD_MEMORY_ERRORS = "Stages with Overhead memory errors";
}