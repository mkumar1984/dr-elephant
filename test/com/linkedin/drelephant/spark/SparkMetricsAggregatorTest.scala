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

import java.util.Date

import scala.collection.JavaConverters

import com.linkedin.drelephant.analysis.ApplicationType
import com.linkedin.drelephant.configurations.aggregator.AggregatorConfigurationData
import com.linkedin.drelephant.math.Statistics
import com.linkedin.drelephant.spark.data.{SparkApplicationData, SparkLogDerivedData, SparkRestDerivedData}
import com.linkedin.drelephant.spark.fetchers.statusapiv1.{ApplicationAttemptInfoImpl, ApplicationInfoImpl, ExecutorSummaryImpl}
import com.linkedin.drelephant.util.MemoryFormatUtils
import org.apache.spark.scheduler.SparkListenerEnvironmentUpdate
import org.apache.commons.io.FileUtils
import org.scalatest.{FunSpec, Matchers}
import com.linkedin.drelephant.spark.fetchers.statusapiv1.TaskMetricDistributionsImpl
import com.linkedin.drelephant.spark.fetchers.statusapiv1.StageDataImpl
import com.linkedin.drelephant.spark.fetchers.statusapiv1.StageStatus
import java.util.Calendar
import org.apache.commons.lang3.time.DateUtils
import com.linkedin.drelephant.AutoTuner

class SparkMetricsAggregatorTest extends FunSpec with Matchers {
  import SparkMetricsAggregatorTest._

  describe("SparkMetricsAggregator") {
    val aggregatorConfigurationData = newFakeAggregatorConfigurationData(
      Map("allocated_memory_waste_buffer_percentage" -> "0.5")
    )

    val appId = "application_1"

    val applicationInfo = {
      val applicationAttemptInfo = {
        val now = System.currentTimeMillis
        val duration = 8000000L
        newFakeApplicationAttemptInfo(Some("1"), startTime = new Date(now - duration), endTime = new Date(now))
      }
      new ApplicationInfoImpl(appId, name = "app", Seq(applicationAttemptInfo))
    }

    val executorSummaries = Seq(
      newFakeExecutorSummary(id = "1", totalDuration = 1000000L, Map("jvmUsedMemory" -> 394567123)),
      newFakeExecutorSummary(id = "2", totalDuration = 3000000L, Map("jvmUsedMemory" -> 23456834))

    )

    val cal = Calendar.getInstance()
    cal.setTimeInMillis(1550340951000L)
    val stage1StartTime = cal.getTime
    val stage1CompletionTime = DateUtils.addMinutes(stage1StartTime, 3)

    val stage2StartTime = DateUtils.addMilliseconds(DateUtils.addMinutes(stage1StartTime, 3), 1)
    val stage2CompletionTime = DateUtils.addMinutes(stage1StartTime, 9)

    val stage3StartTime = DateUtils.addMilliseconds(DateUtils.addMinutes(stage1StartTime, 9), 999)
    val stage3CompletionTime = DateUtils.addMinutes(stage1StartTime, 10)

    val stage4StartTime = DateUtils.addMilliseconds(DateUtils.addMinutes(stage1StartTime, 10), 1)
    val stage4CompletionTime = DateUtils.addMinutes(stage1StartTime, 17)

    val stage5StartTime = DateUtils.addMilliseconds(DateUtils.addMinutes(stage1StartTime, 17), 1)
    val stage5CompletionTime = DateUtils.addMinutes(stage1StartTime, 18)

    val stage6StartTime = DateUtils.addMilliseconds(DateUtils.addMinutes(stage1StartTime, 18), 1)
    val stage6CompletionTime = DateUtils.addMinutes(stage1StartTime, 30)

    val scheduleStage1Delay = IndexedSeq(0D, 0D, 0D, 0D, AutoTuner.ONE_MIN * 1);
    val scheduleStage2Delay = IndexedSeq(0D, 0D, 0D, 0D, AutoTuner.ONE_MIN * 0.7);
    val scheduleStage3Delay = IndexedSeq(0D, 0D, 0D, 0D, 0D);
    val scheduleStage4Delay = IndexedSeq(0D, 0D, 0D, 0D, AutoTuner.ONE_MIN * 3.0);
    val scheduleStage5Delay = IndexedSeq(0D, 0D, 0D, 0D, AutoTuner.ONE_MIN * 1.0);
    val scheduleStage6Delay = IndexedSeq(0D, 0D, 0D, 0D, AutoTuner.ONE_MIN * 0D);

    val taskSummary1 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage1Delay, null, null, null, null, null, null, null);
    val taskSummary2 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage2Delay, null, null, null, null, null, null, null);
    val taskSummary3 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage3Delay, null, null, null, null, null, null, null);
    val taskSummary4 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage4Delay, null, null, null, null, null, null, null);
    val taskSummary5 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage5Delay, null, null, null, null, null, null, null);
    val taskSummary6 = new TaskMetricDistributionsImpl(null, null, null, null, null, null, null, null, null, scheduleStage6Delay, null, null, null, null, null, null, null);
    val stageDatas = Seq(
      newFakeStageData(StageStatus.COMPLETE, 0, 10, Option(stage1StartTime), Option(stage1CompletionTime), "stage1", 1000990929L, 100000L, 2000990929L, 200000L, 4000990929L, 600000L, 24570990929L, 245990929L, 34570990929L, 34570990929L, Option(taskSummary1)),
      newFakeStageData(StageStatus.SKIPPED, 1, 11, Option(stage2StartTime), Option(stage2CompletionTime), "stage2", 2000990929L, 200000L, 3000990929L, 300000L, 5000990929L, 700000L, 23570990929L, 345990929L, 44570990929L, 44570990929L, Option(taskSummary2)),
      newFakeStageData(StageStatus.COMPLETE, 2, 12, Option(stage3StartTime), Option(stage3CompletionTime), "stage3", 3000990929L, 300000L, 4000990929L, 400000L, 6000990929L, 800000L, 22570990929L, 445990929L, 54570990929L, 4570990929L, Option(taskSummary3)),
      newFakeStageData(StageStatus.COMPLETE, 3, 13, Option(stage4StartTime), Option(stage4CompletionTime), "stage4", 4000990929L, 400000L, 5000990929L, 500000L, 7000990929L, 900000L, 21570990929L, 545990929L, 64570990929L, 64570990929L, Option(taskSummary4)),
      newFakeStageData(StageStatus.COMPLETE, 4, 14, Option(stage5StartTime), Option(stage5CompletionTime), "stage5", 5000990929L, 500000L, 6000990929L, 600000L, 8000990929L, 2000000L, 20570990929L, 645990929L, 74570990929L, 4570990929L, Option(taskSummary5)),
      newFakeStageData(StageStatus.COMPLETE, 5, 15, Option(stage6StartTime), Option(stage6CompletionTime), "stage6", 6000990929L, 600000L, 7000990929L, 700000L, 9000990929L, 2100000L, 27570990929L, 845990929L, 84570990929L, 84570990929L, Option(taskSummary6)))


    val restDerivedData = {
      SparkRestDerivedData(
        applicationInfo,
        jobDatas = Seq.empty,
        stageDatas = stageDatas,
        executorSummaries = executorSummaries,
        stagesWithFailedTasks = Seq.empty
      )
    }

    describe("when it has data") {
      val logDerivedData = {
        val environmentUpdate = newFakeSparkListenerEnvironmentUpdate(
          Map(
            "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
            "spark.storage.memoryFraction" -> "0.3",
            "spark.driver.memory" -> "2G",
            "spark.executor.instances" -> "2",
            "spark.executor.memory" -> "4g",
            "spark.shuffle.memoryFraction" -> "0.5"
          )
        )
        SparkLogDerivedData(environmentUpdate)
      }

      val data = SparkApplicationData(appId, restDerivedData, Some(logDerivedData))

      val aggregator = new SparkMetricsAggregator(aggregatorConfigurationData)
      aggregator.aggregate(data)

      val result = aggregator.getResult

      it("calculates resources used (allocated)") {
        result.getResourceUsed should be(4096000+12288000)
      }

      it("calculates resources wasted") {
        val resourceAllocated = 4096000+12288000
        val resourceUsed = 676288+967110
        result.getResourceWasted should be(resourceAllocated.toDouble - resourceUsed.toDouble * 1.5)
      }

      it("calculate total delay") {
        result.getTotalDelay should be(300000L)
      }
      it("sets resource used as 0 when duration is negative") {
        //make the duration negative
        val applicationInfo = {
          val applicationAttemptInfo = {
            val now = System.currentTimeMillis
            val duration = -8000000L
            newFakeApplicationAttemptInfo(Some("1"), startTime = new Date(now - duration), endTime = new Date(now))
          }
          new ApplicationInfoImpl(appId, name = "app", Seq(applicationAttemptInfo))
        }
        val restDerivedData = SparkRestDerivedData(
            applicationInfo,
            jobDatas = Seq.empty,
            stageDatas = Seq.empty,
            executorSummaries = executorSummaries,
            stagesWithFailedTasks = Seq.empty
          )

        val data = SparkApplicationData(appId, restDerivedData, Some(logDerivedData))

        val aggregator = new SparkMetricsAggregator(aggregatorConfigurationData)
        aggregator.aggregate(data)

        val result = aggregator.getResult
        result.getResourceUsed should be(0L)
      }
    }

    describe("when it doesn't have log-derived data") {
      val data = SparkApplicationData(appId, restDerivedData, logDerivedData = None)

      val aggregator = new SparkMetricsAggregator(aggregatorConfigurationData)
      aggregator.aggregate(data)

      val result = aggregator.getResult

      it("doesn't calculate resources used") {
        result.getResourceUsed should be(0L)
      }

      it("doesn't calculate resources wasted") {
        result.getResourceWasted should be(0L)
      }

      it("doesn't calculate total delay") {
        result.getTotalDelay should be(0L)
      }
    }
  }
}

object SparkMetricsAggregatorTest {
  import JavaConverters._

  def newFakeAggregatorConfigurationData(params: Map[String, String] = Map.empty): AggregatorConfigurationData =
      new AggregatorConfigurationData("org.apache.spark.SparkMetricsAggregator", new ApplicationType("SPARK"), params.asJava)

  def newFakeSparkListenerEnvironmentUpdate(appConfigurationProperties: Map[String, String]): SparkListenerEnvironmentUpdate =
    SparkListenerEnvironmentUpdate(Map("Spark Properties" -> appConfigurationProperties.toSeq))

  def newFakeApplicationAttemptInfo(
    attemptId: Option[String],
    startTime: Date,
    endTime: Date
  ): ApplicationAttemptInfoImpl = new ApplicationAttemptInfoImpl(
    attemptId,
    startTime,
    endTime,
    sparkUser = "foo",
    completed = true
  )

  def newFakeExecutorSummary(
    id: String,
    totalDuration: Long,
    peakJvmUsedMemory: Map[String, Long]
  ): ExecutorSummaryImpl = new ExecutorSummaryImpl(
    id,
    hostPort = "",
    rddBlocks = 0,
    memoryUsed = 0,
    diskUsed = 0,
    totalCores = 1,
    activeTasks = 0,
    failedTasks = 0,
    completedTasks = 0,
    totalTasks = 0,
    maxTasks = 0,
    totalDuration,
    totalInputBytes = 0,
    totalShuffleRead = 0,
    totalShuffleWrite = 0,
    maxMemory = 0,
    totalGCTime = 0,
    totalMemoryBytesSpilled = 0,
    executorLogs = Map.empty,
    peakJvmUsedMemory,
    peakUnifiedMemory = Map.empty
  )

  def newFakeStageData(
    status: StageStatus,
    stageId: Int,
    numCompleteTasks: Int,
    submissionTime: Option[Date],
    completionTime: Option[Date],
    name: String,
    inputBytes: Long,
    inputRecords: Long,
    outputBytes: Long,
    outputRecords: Long,
    shuffleReadBytes: Long,
    shuffleReadRecords: Long,
    shuffleWriteBytes: Long,
    shuffleWriteRecords: Long,
    memoryBytesSpilled: Long,
    diskBytesSpilled: Long,
    taskSummary: Option[TaskMetricDistributionsImpl]): StageDataImpl = new StageDataImpl(
    status,
    stageId,
    attemptId = 0,
    numTasks = numCompleteTasks + 0,
    numActiveTasks = numCompleteTasks + 0,
    numCompleteTasks,
    numFailedTasks = 0,
    executorRunTime = 0L,
    executorCpuTime = 0,
    submissionTime,
    firstTaskLaunchedTime = None,
    completionTime,
    failureReason = None,
    inputBytes,
    inputRecords,
    outputBytes,
    outputRecords,
    shuffleReadBytes,
    shuffleReadRecords,
    shuffleWriteBytes,
    shuffleWriteRecords,
    memoryBytesSpilled,
    diskBytesSpilled,
    name,
    details = "",
    schedulingPool = "",
    accumulatorUpdates = Seq.empty,
    tasks = None,
    executorSummary = None,
    peakJvmUsedMemory = None,
    peakExecutionMemory = None,
    peakStorageMemory = None,
    peakUnifiedMemory = None,
    taskSummary,
    executorMetricsSummary = None)
}
