/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.spark

import com.linkedin.drelephant.analysis.{ HadoopAggregatedData, HadoopApplicationData, HadoopMetricsAggregator }
import com.linkedin.drelephant.configurations.aggregator.AggregatorConfigurationData
import com.linkedin.drelephant.math.Statistics
import com.linkedin.drelephant.spark.data.{ SparkApplicationData }
import com.linkedin.drelephant.spark.fetchers.statusapiv1.ExecutorSummary
import com.linkedin.drelephant.util.MemoryFormatUtils
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger

import scala.util.Try
import com.linkedin.drelephant.spark.fetchers.statusapiv1.StageData
import java.util.Date
import com.linkedin.drelephant.AutoTuner
import com.linkedin.drelephant.spark.fetchers.statusapiv1.StageStatus

class SparkMetricsAggregator(private val aggregatorConfigurationData: AggregatorConfigurationData)
    extends HadoopMetricsAggregator {
  import SparkMetricsAggregator._

  private val logger: Logger = Logger.getLogger(classOf[SparkMetricsAggregator])

  private val allocatedMemoryWasteBufferPercentage: Double =
    Option(aggregatorConfigurationData.getParamMap.get(ALLOCATED_MEMORY_WASTE_BUFFER_PERCENTAGE_KEY))
      .flatMap { value => Try(value.toDouble).toOption }
      .getOrElse(DEFAULT_ALLOCATED_MEMORY_WASTE_BUFFER_PERCENTAGE)

  private val hadoopAggregatedData: HadoopAggregatedData = new HadoopAggregatedData()

  override def getResult(): HadoopAggregatedData = hadoopAggregatedData

  override def aggregate(data: HadoopApplicationData): Unit = data match {
    case (data: SparkApplicationData) => aggregate(data)
    case _ => throw new IllegalArgumentException("data should be SparkApplicationData")
  }

  private def aggregate(data: SparkApplicationData): Unit = for {
    executorMemoryBytes <- executorMemoryBytesOf(data)
  } {
    val applicationDurationMillis = applicationDurationMillisOf(data)
    if (applicationDurationMillis < 0) {
      logger.warn(s"applicationDurationMillis is negative. Skipping Metrics Aggregation:${applicationDurationMillis}")
    } else {
      var (resourcesActuallyUsed, resourcesAllocatedForUse) = calculateResourceUsage(data.executorSummaries, executorMemoryBytes)
      val resourcesActuallyUsedWithBuffer = resourcesActuallyUsed.doubleValue() * (1.0 + allocatedMemoryWasteBufferPercentage)
      val resourcesWastedMBSeconds = (resourcesActuallyUsedWithBuffer < resourcesAllocatedForUse.doubleValue()) match {
        case true => resourcesAllocatedForUse.doubleValue() - resourcesActuallyUsedWithBuffer
        case false => 0.0
      }
      //allocated is the total used resource from the cluster.
      if (resourcesAllocatedForUse.isValidLong) {
        hadoopAggregatedData.setResourceUsed(resourcesAllocatedForUse.toLong)
      } else {
        logger.warn(s"resourcesAllocatedForUse/resourcesWasted exceeds Long.MaxValue")
        logger.warn(s"ResourceUsed: ${resourcesAllocatedForUse}")
        logger.warn(s"executorMemoryBytes:${executorMemoryBytes}")
        logger.warn(s"applicationDurationMillis:${applicationDurationMillis}")
        logger.warn(s"resourcesActuallyUsedWithBuffer:${resourcesActuallyUsedWithBuffer}")
        logger.warn(s"resourcesWastedMBSeconds:${resourcesWastedMBSeconds}")
        logger.warn(s"allocatedMemoryWasteBufferPercentage:${allocatedMemoryWasteBufferPercentage}")
      }
      hadoopAggregatedData.setResourceWasted(resourcesWastedMBSeconds.toLong)
      hadoopAggregatedData.setTotalDelay(computeTotalWaitingTime(data.stageDatas))
    }
  }

  //calculates the resource usage by summing up the resources used per executor
  private def calculateResourceUsage(executorSummaries: Seq[ExecutorSummary], executorMemoryBytes: Long): (BigInt, BigInt) = {
    var sumResourceUsage: BigInt = 0
    var sumResourcesAllocatedForUse: BigInt = 0
    executorSummaries.foreach(
      executorSummary => {
        var memUsedBytes: Long = executorSummary.peakJvmUsedMemory.getOrElse(JVM_USED_MEMORY, 0).asInstanceOf[Number].longValue + MemoryFormatUtils.stringToBytes(SPARK_RESERVED_MEMORY)
        var timeSpent: Long = executorSummary.totalDuration
        var totalCores: Int = executorSummary.totalCores
        if(totalCores==0){
          totalCores=1;
        }
        val bytesMillisUsed = BigInt(memUsedBytes) * timeSpent/totalCores
        val bytesMillisAllocated = BigInt(executorMemoryBytes) * timeSpent/totalCores
        sumResourcesAllocatedForUse += (bytesMillisAllocated / (BigInt(FileUtils.ONE_MB) * BigInt(Statistics.SECOND_IN_MS)))
        sumResourceUsage += (bytesMillisUsed / (BigInt(FileUtils.ONE_MB) * BigInt(Statistics.SECOND_IN_MS)))
      })
    (sumResourceUsage, sumResourcesAllocatedForUse)
  }

  private def aggregateresourcesAllocatedForUse(
    executorInstances: Int,
    executorMemoryBytes: Long,
    applicationDurationMillis: Long): BigInt = {
    val bytesMillis = BigInt(executorInstances) * BigInt(executorMemoryBytes) * BigInt(applicationDurationMillis)
    (bytesMillis / (BigInt(FileUtils.ONE_MB) * BigInt(Statistics.SECOND_IN_MS)))
  }

  private def executorInstancesOf(data: SparkApplicationData): Option[Int] = {
    val appConfigurationProperties = data.appConfigurationProperties
    appConfigurationProperties.get(SPARK_EXECUTOR_INSTANCES_KEY).map(_.toInt)
  }

  private def executorMemoryBytesOf(data: SparkApplicationData): Option[Long] = {
    val appConfigurationProperties = data.appConfigurationProperties
    appConfigurationProperties.get(SPARK_EXECUTOR_MEMORY_KEY).map(MemoryFormatUtils.stringToBytes)
  }

  private def applicationDurationMillisOf(data: SparkApplicationData): Long = {
    require(data.applicationInfo.attempts.nonEmpty)
    val lastApplicationAttemptInfo = data.applicationInfo.attempts.last
    lastApplicationAttemptInfo.endTime.getTime - lastApplicationAttemptInfo.startTime.getTime
  }

  private def totalExecutorTaskTimeMillisOf(data: SparkApplicationData): BigInt = {
    data.executorSummaries.map { executorSummary => BigInt(executorSummary.totalDuration) }.sum
  }

  /**
   * This method computes total waiting time by looking at each stage's waiting time and using dependencies between
   * each stage. Total waiting time is the difference between actual duration of the job and duration of the job
   * if there was no scheduler delay.
   */
  def computeTotalWaitingTime(stageDatas: Seq[StageData]) = {
    val notSkippedStages = for (stage <- stageDatas if !stage.status.equals(StageStatus.SKIPPED)) yield stage
    val dependencyMap = getStageDependency(notSkippedStages);

    val sortedStages = notSkippedStages.sortBy { _.submissionTime }
    var completionTimeWithoutDelayMap: scala.collection.mutable.Map[Int, Long] = scala.collection.mutable.Map[Int, Long]()
    var completionTimeWithDelayMap: scala.collection.mutable.Map[Int, Long] = scala.collection.mutable.Map[Int, Long]()
    var finalStageCompletionTimeWithoutDelay = 0L;
    var finalStageActualCompletionTime = 0L;

    for (i <- 0 until sortedStages.length) {
      val stage = sortedStages(i)
      val stageId = stage.stageId
      var maxCompletionTimeWithoutDelay: Long = 0L;
      var maxCompletionWithDelay: Long = 0L;

      for (dependeeStageId <- dependencyMap.get(stageId).get) {
        maxCompletionTimeWithoutDelay = Math.max(maxCompletionTimeWithoutDelay, completionTimeWithoutDelayMap.getOrElse(dependeeStageId, 0L));
        maxCompletionWithDelay = Math.max(maxCompletionWithDelay, completionTimeWithDelayMap.getOrElse(dependeeStageId, 0L));
      }
      if (maxCompletionTimeWithoutDelay == 0) {
        maxCompletionTimeWithoutDelay = stage.submissionTime.get.getTime;
        maxCompletionWithDelay = stage.submissionTime.get.getTime
      }
      val currentStageDelay = getStageDelay(stage)
      val completionTimeWithoutDelay = maxCompletionTimeWithoutDelay + (stage.completionTime.get.getTime - stage.submissionTime.get.getTime) - currentStageDelay + (stage.submissionTime.get.getTime - maxCompletionWithDelay)

      logger.debug("currentStageDelay for " + stageId + ":" + currentStageDelay)
      logger.debug("Delay till this stage " + stageId + ":" + (stage.completionTime.get.getTime - completionTimeWithoutDelay))

      completionTimeWithoutDelayMap.put(stageId, completionTimeWithoutDelay)
      completionTimeWithDelayMap.put(stageId, stage.completionTime.get.getTime)

      finalStageCompletionTimeWithoutDelay = Math.max(finalStageCompletionTimeWithoutDelay, completionTimeWithoutDelay);
      finalStageActualCompletionTime = Math.max(finalStageActualCompletionTime, stage.completionTime.get.getTime)
    }

    logger.debug("Total Delay:" + (finalStageActualCompletionTime - finalStageCompletionTimeWithoutDelay))
    finalStageActualCompletionTime - finalStageCompletionTimeWithoutDelay
  }

  /**
   * Returns scheduler delay for the job, which is max of scheduler delay distribution
   */
  def getStageDelay(stage: StageData): Long = {
    val maxSchedulerDelayIndex = 4
    var schedulerDelay = 0D
    val taskSummary = stage.taskSummary.getOrElse(null)
    if (taskSummary != null) {
      val schedulerDelays = taskSummary.schedulerDelay
      if (schedulerDelays != null)
        schedulerDelay = schedulerDelays(maxSchedulerDelayIndex)
    }
    schedulerDelay.longValue()
  }

  /**
   * Get approximate dependency between each stages, using submission time and completion time
   * A stage is dependent on other stage if it is started within 1 second of any stage's completion.
   */
  def getStageDependency(stageDatas: Seq[StageData]): scala.collection.mutable.Map[Int, scala.collection.mutable.Set[Int]] = {
    var dependencyMap: scala.collection.mutable.Map[Int, scala.collection.mutable.Set[Int]] = scala.collection.mutable.Map[Int, scala.collection.mutable.Set[Int]]();
    for (i <- 0 until stageDatas.length) {
      var dependentStages = findDependeeStages(i, stageDatas);
      dependencyMap.put(stageDatas(i).stageId, dependentStages)
    }
    logger.debug("Depdendency Map " + dependencyMap)
    dependencyMap
  }

  /**
   * Find dependee stages for the givem stage
   */
  def findDependeeStages(dependeeStageIndex: Int, stages: Seq[StageData]): scala.collection.mutable.Set[Int] = {
    val dependentStage = stages(dependeeStageIndex)
    val dependentStageStartTime: Date = dependentStage.submissionTime.getOrElse(null)
    var dependeeStages: scala.collection.mutable.Set[Int] = scala.collection.mutable.Set[Int]()
    var timeBasedDependentStage = -1
    var timeBasedDependetStageCompletionTime = new Date(0);

    for (j <- 0 until stages.length if dependeeStageIndex != j) {
      val possibleDependeeStage = stages(j)
      val possiblDependeeStageCompletionTime: Date = possibleDependeeStage.completionTime.getOrElse(null)
      logger.debug("Current dependent Stage " + j + " date " + possiblDependeeStageCompletionTime);
      if (dependentStageStartTime.before(possiblDependeeStageCompletionTime) == false) {
        if (possiblDependeeStageCompletionTime.after(timeBasedDependetStageCompletionTime)) {
          timeBasedDependentStage = j;
          timeBasedDependetStageCompletionTime = possiblDependeeStageCompletionTime;
        }
        if (dependentStageStartTime.getTime <= possiblDependeeStageCompletionTime.getTime + AutoTuner.ONE_SEC) {
          dependeeStages.add(possibleDependeeStage.stageId)
        }
      }
    }
    if (dependeeStages.size == 0 && timeBasedDependentStage != -1) {
      dependeeStages.add(timeBasedDependentStage)
    }
    dependeeStages
  }
}

object SparkMetricsAggregator {
  /** The percentage of allocated memory we expect to waste because of overhead. */
  val DEFAULT_ALLOCATED_MEMORY_WASTE_BUFFER_PERCENTAGE = 0.5D
  val ALLOCATED_MEMORY_WASTE_BUFFER_PERCENTAGE_KEY = "allocated_memory_waste_buffer_percentage"
  val SPARK_RESERVED_MEMORY: String = "300M"
  val SPARK_EXECUTOR_INSTANCES_KEY = "spark.executor.instances"
  val SPARK_EXECUTOR_MEMORY_KEY = "spark.executor.memory"
  val JVM_USED_MEMORY = "jvmUsedMemory"
}
