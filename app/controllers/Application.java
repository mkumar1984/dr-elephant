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

package controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import models.AppHeuristicResult;
import models.AppResult;
import models.Job;
import models.JobExecution;

import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import play.api.templates.Html;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;
import scala.xml.PrettyPrinter;
import views.html.help.metrics.helpRuntime;
import views.html.help.metrics.helpUsedResources;
import views.html.help.metrics.helpWaittime;
import views.html.help.metrics.helpWastedResources;
import views.html.page.comparePage;
import views.html.page.flowHistoryPage;
import views.html.page.helpPage;
import views.html.page.homePage;
import views.html.page.jobHistoryPage;
import views.html.page.oldFlowHistoryPage;
import views.html.page.oldHelpPage;
import views.html.page.oldJobHistoryPage;
import views.html.page.searchPage;
import views.html.results.compareResults;
import views.html.results.flowDetails;
import views.html.results.flowHistoryResults;
import views.html.results.flowMetricsHistoryResults;
import views.html.results.jobDetails;
import views.html.results.jobHistoryResults;
import views.html.results.jobMetricsHistoryResults;
import views.html.results.oldFlowHistoryResults;
import views.html.results.oldFlowMetricsHistoryResults;
import views.html.results.oldJobHistoryResults;
import views.html.results.oldJobMetricsHistoryResults;
import views.html.results.searchResults;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Query;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linkedin.drelephant.ElephantContext;
import com.linkedin.drelephant.analysis.Metrics;
import com.linkedin.drelephant.analysis.Severity;
import com.linkedin.drelephant.tunin.AutoTuningAPIHelper;
import com.linkedin.drelephant.tunin.FitnessComputeUtil;
import com.linkedin.drelephant.tunin.JobCompleteDetector;
import com.linkedin.drelephant.tunin.PSOParamGenerator;
import com.linkedin.drelephant.tunin.ParamGenerator;
import com.linkedin.drelephant.tunin.TunerState;
import com.linkedin.drelephant.util.Utils;


public class Application extends Controller {
  private static final Logger logger = Logger.getLogger(Application.class);
  private static final long DAY = 24 * 60 * 60 * 1000;
  private static final long FETCH_DELAY = 60 * 1000;

  private static final int PAGE_LENGTH = 20;                  // Num of jobs in a search page
  private static final int PAGE_BAR_LENGTH = 5;               // Num of pages shown in the page bar
  private static final int REST_PAGE_LENGTH = 100;            // Num of jobs in a rest search page
  private static final int JOB_HISTORY_LIMIT = 5000;          // Set to avoid memory error.
  private static final int MAX_HISTORY_LIMIT = 15;            // Upper limit on the number of executions to display
  private static final int STAGE_LIMIT = 25;                  // Upper limit on the number of stages to display

  // Form and Rest parameters
  public static final String APP_ID = "id";
  public static final String FLOW_DEF_ID = "flow-def-id";
  public static final String FLOW_EXEC_ID = "flow-exec-id";
  public static final String JOB_DEF_ID = "job-def-id";
  public static final String USERNAME = "username";
  public static final String QUEUE_NAME = "queue-name";
  public static final String SEVERITY = "severity";
  public static final String JOB_TYPE = "job-type";
  public static final String ANALYSIS = "analysis";
  public static final String STARTED_TIME_BEGIN = "started-time-begin";
  public static final String STARTED_TIME_END = "started-time-end";
  public static final String FINISHED_TIME_BEGIN = "finished-time-begin";
  public static final String FINISHED_TIME_END = "finished-time-end";
  public static final String COMPARE_FLOW_ID1 = "flow-exec-id1";
  public static final String COMPARE_FLOW_ID2 = "flow-exec-id2";
  public static final String PAGE = "page";

  private enum Version {OLD,NEW};

  // Configuration properties
  private static final String SEARCH_MATCHES_PARTIAL_CONF = "drelephant.application.search.match.partial";

  private static long _lastFetch = 0;
  private static int _numJobsAnalyzed = 0;
  private static int _numJobsCritical = 0;
  private static int _numJobsSevere = 0;


  /**
  * Serves the initial index.html page for the new user interface. This page contains the whole web app
  */
  public static Result serveAsset(String path) {
    return ok(index.render());
  }

  /**
   * Controls the Home page of Dr. Elephant.
   *
   * Displays the latest jobs which were analysed in the last 24 hours.
   */
  public static Result dashboard() {
    long now = System.currentTimeMillis();
    long finishDate = now - DAY;

    // Update statistics only after FETCH_DELAY
    if (now - _lastFetch > FETCH_DELAY) {
      _numJobsAnalyzed = AppResult.find.where().gt(AppResult.TABLE.FINISH_TIME, finishDate).findRowCount();
      _numJobsCritical = AppResult.find.where()
          .gt(AppResult.TABLE.FINISH_TIME, finishDate)
          .eq(AppResult.TABLE.SEVERITY, Severity.CRITICAL.getValue())
          .findRowCount();
      _numJobsSevere = AppResult.find.where()
          .gt(AppResult.TABLE.FINISH_TIME, finishDate)
          .eq(AppResult.TABLE.SEVERITY, Severity.SEVERE.getValue())
          .findRowCount();
      _lastFetch = now;
    }

    // Fetch only required fields for jobs analysed in the last 24 hours up to a max of 50 jobs
    List<AppResult> results = AppResult.find.select(AppResult.getSearchFields())
        .where()
        .gt(AppResult.TABLE.FINISH_TIME, finishDate)
        .order()
        .desc(AppResult.TABLE.FINISH_TIME)
        .setMaxRows(50)
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
        .findList();

    return ok(homePage.render(_numJobsAnalyzed, _numJobsSevere, _numJobsCritical,
        searchResults.render("Latest analysis", results)));
  }

  /**
   * Returns the scheduler info id/url pair for the most recent app result that has an id like value
   * (which can use % and _ SQL wild cards) for the specified field. Note that this is a pair rather
   * than merely an ID/URL because for some schedulers (e.g. Airflow) they are not equivalent and
   * usually the UI wants to display the ID with a link to the URL. While it is true that the URL
   * can probably be derived from the ID in most cases, we would need scheduler specific logic which
   * would be a mess.
   */
  private static IdUrlPair bestSchedulerInfoMatchLikeValue(String value, String schedulerIdField) {
    String schedulerUrlField;
    if (schedulerIdField.equals(AppResult.TABLE.FLOW_DEF_ID)) {
      schedulerUrlField = AppResult.TABLE.FLOW_DEF_URL;
    } else if (schedulerIdField.equals(AppResult.TABLE.FLOW_EXEC_ID)) {
      schedulerUrlField = AppResult.TABLE.FLOW_EXEC_URL;
    } else if (schedulerIdField.equals(AppResult.TABLE.JOB_DEF_ID)) {
      schedulerUrlField = AppResult.TABLE.JOB_DEF_URL;
    } else if (schedulerIdField.equals(AppResult.TABLE.JOB_EXEC_ID)) {
      schedulerUrlField = AppResult.TABLE.JOB_EXEC_URL;
    } else {
      throw new RuntimeException(String.format("%s is not a valid scheduler info id field", schedulerIdField));
    }
    AppResult result = AppResult.find
            .select(String.format("%s, %s", schedulerIdField, schedulerUrlField))
            .where().like(schedulerIdField, value)
            .order()
            .desc(AppResult.TABLE.FINISH_TIME)
            .setMaxRows(1)
            .findUnique();
    if (result != null) {
      if (schedulerIdField.equals(AppResult.TABLE.FLOW_DEF_ID)) {
        return new IdUrlPair(result.flowDefId, result.flowDefUrl);
      } else if (schedulerIdField.equals(AppResult.TABLE.FLOW_EXEC_ID)) {
        return new IdUrlPair(result.flowExecId, result.flowExecUrl);
      } else if (schedulerIdField.equals(AppResult.TABLE.JOB_DEF_ID)) {
        return new IdUrlPair(result.jobDefId, result.jobDefUrl);
      } else if (schedulerIdField.equals(AppResult.TABLE.JOB_EXEC_ID)) {
        return new IdUrlPair(result.jobExecId, result.jobExecUrl);
      }
    }
    return null;
  }

  /**
   * Given a (possibly) partial scheduler info id, try to find the closest existing id.
   */
  private static IdUrlPair bestSchedulerInfoMatchGivenPartialId(String partialSchedulerInfoId, String schedulerInfoIdField) {
    IdUrlPair schedulerInfoPair;
    // check for exact match
    schedulerInfoPair = bestSchedulerInfoMatchLikeValue(partialSchedulerInfoId, schedulerInfoIdField);
    // check for suffix match if feature isn't disabled
    if (schedulerInfoPair == null && ElephantContext.instance().getGeneralConf().getBoolean(SEARCH_MATCHES_PARTIAL_CONF, true)) {
      schedulerInfoPair = bestSchedulerInfoMatchLikeValue(String.format("%s%%", partialSchedulerInfoId), schedulerInfoIdField);
    }
    // if we didn't find anything just give a buest guess
    if (schedulerInfoPair == null) {
      schedulerInfoPair = new IdUrlPair(partialSchedulerInfoId, "");
    }
    return schedulerInfoPair;
  }

  /**
   * Controls the Search Feature
   */
  public static Result search() {
    DynamicForm form = Form.form().bindFromRequest(request());
    String appId = form.get(APP_ID);
    appId = appId != null ? appId.trim() : "";
    if (appId.contains("job")) {
      appId = appId.replaceAll("job", "application");
    }
    String partialFlowExecId = form.get(FLOW_EXEC_ID);
    partialFlowExecId = (partialFlowExecId != null) ? partialFlowExecId.trim() : null;

    // Search and display job details when job id or flow execution url is provided.
    if (!appId.isEmpty()) {
      AppResult result = AppResult.find.select("*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
              "*")
          .where()
          .idEq(appId).findUnique();
      return ok(searchPage.render(null, jobDetails.render(result)));
    } else if (Utils.isSet(partialFlowExecId)) {
      IdUrlPair flowExecPair = bestSchedulerInfoMatchGivenPartialId(partialFlowExecId, AppResult.TABLE.FLOW_EXEC_ID);
      List<AppResult> results = AppResult.find
          .select(AppResult.getSearchFields() + "," + AppResult.TABLE.JOB_EXEC_ID)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
          .where()
          .eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecPair.getId())
          .findList();
      Map<IdUrlPair, List<AppResult>> map = ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.JOB_EXECUTION_ID);
      return ok(searchPage.render(null, flowDetails.render(flowExecPair, map)));
    }

    // Prepare pagination of results
    PaginationStats paginationStats = new PaginationStats(PAGE_LENGTH, PAGE_BAR_LENGTH);
    int pageLength = paginationStats.getPageLength();
    paginationStats.setCurrentPage(1);
    final Map<String, String[]> searchString = request().queryString();
    if (searchString.containsKey(PAGE)) {
      try {
        paginationStats.setCurrentPage(Integer.parseInt(searchString.get(PAGE)[0]));
      } catch (NumberFormatException ex) {
        logger.error("Error parsing page number. Setting current page to 1.");
        paginationStats.setCurrentPage(1);
      }
    }
    int currentPage = paginationStats.getCurrentPage();
    int paginationBarStartIndex = paginationStats.getPaginationBarStartIndex();

    // Filter jobs by search parameters
    Query<AppResult> query = generateSearchQuery(AppResult.getSearchFields(), getSearchParams());
    List<AppResult> results = query.setFirstRow((paginationBarStartIndex - 1) * pageLength)
        .setMaxRows((paginationStats.getPageBarLength() - 1) * pageLength + 1)
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
        .findList();
    paginationStats.setQueryString(getQueryString());
    if (results.isEmpty() || currentPage > paginationStats.computePaginationBarEndIndex(results.size())) {
      return ok(searchPage.render(null, jobDetails.render(null)));
    } else {
      List<AppResult> resultsToDisplay = results.subList((currentPage - paginationBarStartIndex) * pageLength,
              Math.min(results.size(), (currentPage - paginationBarStartIndex + 1) * pageLength));
      return ok(searchPage.render(paginationStats, searchResults.render(
              String.format("Results: Showing %,d of %,d", resultsToDisplay.size(), query.findRowCount()), resultsToDisplay)));
    }
  }

  /**
   * Parses the request for the queryString
   *
   * @return URL Encoded String of Parameter Value Pair
   */
  public static String getQueryString() {
    List<BasicNameValuePair> fields = new LinkedList<BasicNameValuePair>();
    final Set<Map.Entry<String, String[]>> entries = request().queryString().entrySet();
    for (Map.Entry<String, String[]> entry : entries) {
      final String key = entry.getKey();
      final String value = entry.getValue()[0];
      if (!key.equals(PAGE)) {
        fields.add(new BasicNameValuePair(key, value));
      }
    }
    if (fields.isEmpty()) {
      return null;
    } else {
      return URLEncodedUtils.format(fields, "utf-8");
    }
  }

  public static Map<String, String> getSearchParams() {
    Map<String, String> searchParams = new HashMap<String, String>();

    DynamicForm form = Form.form().bindFromRequest(request());
    String username = form.get(USERNAME);
    username = username != null ? username.trim().toLowerCase() : null;
    searchParams.put(USERNAME, username);
    String queuename = form.get(QUEUE_NAME);
    queuename = queuename != null ? queuename.trim().toLowerCase() : null;
    searchParams.put(QUEUE_NAME, queuename);
    searchParams.put(SEVERITY, form.get(SEVERITY));
    searchParams.put(JOB_TYPE, form.get(JOB_TYPE));
    searchParams.put(ANALYSIS, form.get(ANALYSIS));
    searchParams.put(FINISHED_TIME_BEGIN, form.get(FINISHED_TIME_BEGIN));
    searchParams.put(FINISHED_TIME_END, form.get(FINISHED_TIME_END));
    searchParams.put(STARTED_TIME_BEGIN, form.get(STARTED_TIME_BEGIN));
    searchParams.put(STARTED_TIME_END, form.get(STARTED_TIME_END));

    return searchParams;
  }

  /**
   * Build SQL predicates for Search Query
   *
   * @param selectParams The fields to select from the table
   * @param searchParams The fields to query on the table
   * @return An sql expression on App Result
   */
  public static Query<AppResult> generateSearchQuery(String selectParams, Map<String, String> searchParams) {
    if (searchParams == null || searchParams.isEmpty()) {
      return AppResult.find.select(selectParams).order().desc(AppResult.TABLE.FINISH_TIME);
    }
    ExpressionList<AppResult> query = AppResult.find.select(selectParams).where();

    // Build predicates
    String username = searchParams.get(USERNAME);
    if (Utils.isSet(username)) {
      query = query.eq(AppResult.TABLE.USERNAME, username);
    }

    String queuename = searchParams.get(QUEUE_NAME);
    if (Utils.isSet(queuename)) {
      query = query.eq(AppResult.TABLE.QUEUE_NAME, queuename);
    }
    String jobType = searchParams.get(JOB_TYPE);
    if (Utils.isSet(jobType)) {
      query = query.eq(AppResult.TABLE.JOB_TYPE, jobType);
    }
    String severity = searchParams.get(SEVERITY);
    if (Utils.isSet(severity)) {
      String analysis = searchParams.get(ANALYSIS);
      if (Utils.isSet(analysis)) {
        query =
            query.eq(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.HEURISTIC_NAME, analysis)
                .ge(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.SEVERITY, severity);
      } else {
        query = query.ge(AppResult.TABLE.SEVERITY, severity);
      }
    }

    // Time Predicates. Both the startedTimeBegin and startedTimeEnd are inclusive in the filter
    String startedTimeBegin = searchParams.get(STARTED_TIME_BEGIN);
    if (Utils.isSet(startedTimeBegin)) {
      long time = parseTime(startedTimeBegin);
      if (time > 0) {
        query = query.ge(AppResult.TABLE.START_TIME, time);
      }
    }
    String startedTimeEnd = searchParams.get(STARTED_TIME_END);
    if (Utils.isSet(startedTimeEnd)) {
      long time = parseTime(startedTimeEnd);
      if (time > 0) {
        query = query.le(AppResult.TABLE.START_TIME, time);
      }
    }

    String finishedTimeBegin = searchParams.get(FINISHED_TIME_BEGIN);
    if (Utils.isSet(finishedTimeBegin)) {
      long time = parseTime(finishedTimeBegin);
      if (time > 0) {
        query = query.ge(AppResult.TABLE.FINISH_TIME, time);
      }
    }
    String finishedTimeEnd = searchParams.get(FINISHED_TIME_END);
    if (Utils.isSet(finishedTimeEnd)) {
      long time = parseTime(finishedTimeEnd);
      if (time > 0) {
        query = query.le(AppResult.TABLE.FINISH_TIME, time);
      }
    }

    // If queried by start time then sort the results by start time.
    if (Utils.isSet(startedTimeBegin) || Utils.isSet(startedTimeEnd)) {
      return query.order().desc(AppResult.TABLE.START_TIME);
    } else {
      return query.order().desc(AppResult.TABLE.FINISH_TIME);
    }
  }

  /**
   Controls the Compare Feature
   */
  public static Result compare() {
    DynamicForm form = Form.form().bindFromRequest(request());
    String partialFlowExecId1 = form.get(COMPARE_FLOW_ID1);
    partialFlowExecId1 = (partialFlowExecId1 != null) ? partialFlowExecId1.trim() : null;
    String partialFlowExecId2 = form.get(COMPARE_FLOW_ID2);
    partialFlowExecId2 = (partialFlowExecId2 != null) ? partialFlowExecId2.trim() : null;

    List<AppResult> results1 = null;
    List<AppResult> results2 = null;
    if (partialFlowExecId1 != null && !partialFlowExecId1.isEmpty() && partialFlowExecId2 != null && !partialFlowExecId2.isEmpty()) {
      IdUrlPair flowExecIdPair1 = bestSchedulerInfoMatchGivenPartialId(partialFlowExecId1, AppResult.TABLE.FLOW_EXEC_ID);
      IdUrlPair flowExecIdPair2 = bestSchedulerInfoMatchGivenPartialId(partialFlowExecId2, AppResult.TABLE.FLOW_EXEC_ID);
      results1 = AppResult.find
          .select(AppResult.getSearchFields() + "," + AppResult.TABLE.JOB_DEF_ID + "," + AppResult.TABLE.JOB_DEF_URL
              + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL)
          .where().eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecIdPair1.getId()).setMaxRows(100)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
          .findList();
      results2 = AppResult.find
          .select(
              AppResult.getSearchFields() + "," + AppResult.TABLE.JOB_DEF_ID + "," + AppResult.TABLE.JOB_DEF_URL + ","
                  + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL)
          .where().eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecIdPair2.getId()).setMaxRows(100)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
          .findList();
    }
    return ok(comparePage.render(compareResults.render(compareFlows(results1, results2))));
  }

  /**
   * Helper Method for the compare controller.
   * This Compares 2 flow executions at job level.
   *
   * @param results1 The list of jobs under flow execution 1
   * @param results2 The list of jobs under flow execution 2
   * @return A map of Job Urls to the list of jobs corresponding to the 2 flow execution urls
   */
  private static Map<IdUrlPair, Map<IdUrlPair, List<AppResult>>> compareFlows(List<AppResult> results1, List<AppResult> results2) {

    Map<IdUrlPair, Map<IdUrlPair, List<AppResult>>> jobDefMap = new HashMap<IdUrlPair, Map<IdUrlPair, List<AppResult>>>();

    if (results1 != null && !results1.isEmpty() && results2 != null && !results2.isEmpty()) {

      IdUrlPair flow1 = new IdUrlPair(results1.get(0).flowExecId, results1.get(0).flowExecUrl);
      IdUrlPair flow2 = new IdUrlPair(results2.get(0).flowExecId, results2.get(0).flowExecUrl);

      Map<IdUrlPair, List<AppResult>> map1 = ControllerUtil.groupJobs(results1, ControllerUtil.GroupBy.JOB_DEFINITION_ID);
      Map<IdUrlPair, List<AppResult>> map2 = ControllerUtil.groupJobs(results2, ControllerUtil.GroupBy.JOB_DEFINITION_ID);

      final Set<IdUrlPair> group1 = new TreeSet<IdUrlPair>(new Comparator<IdUrlPair>() {
        public int compare(final IdUrlPair o1, final IdUrlPair o2) {
          return o1.getId().compareToIgnoreCase(o2.getId());
        }
      });
      group1.addAll(map1.keySet());
      final Set<IdUrlPair> group2 = new TreeSet<IdUrlPair>(new Comparator<IdUrlPair>() {
        public int compare(final IdUrlPair o1, final IdUrlPair o2) {
          return o1.getId().compareToIgnoreCase(o2.getId());
        }
      });
      group2.addAll(map2.keySet());

      // Display jobs that are common to the two flows first followed by jobs in flow 1 and flow 2.
      Set<IdUrlPair> CommonJobs = Sets.intersection(group1, group2);
      Set<IdUrlPair> orderedFlowSet = Sets.union(CommonJobs, group1);
      Set<IdUrlPair> union = Sets.union(orderedFlowSet, group2);

      for (IdUrlPair pair : union) {
        Map<IdUrlPair, List<AppResult>> flowExecMap = new LinkedHashMap<IdUrlPair, List<AppResult>>();
        flowExecMap.put(flow1, map1.get(pair));
        flowExecMap.put(flow2, map2.get(pair));
        jobDefMap.put(pair, flowExecMap);
      }
    }
    return jobDefMap;
  }

  /**
   * Returns the new version of flow history
   */
  public static Result flowHistory() {
    return getFlowHistory(Version.NEW);
  }

  /**
   * Returns the old version of flow history
   */
  public static Result oldFlowHistory() {
    return getFlowHistory(Version.OLD);
  }

  /**
   * Returns the flowHistory based on the version provided
   *
   * @param version Can be either new or old
   * @return The flowhistory page based on the version provided
   */
  private static Result getFlowHistory(Version version) {
    DynamicForm form = Form.form().bindFromRequest(request());
    String partialFlowDefId = form.get(FLOW_DEF_ID);
    partialFlowDefId = (partialFlowDefId != null) ? partialFlowDefId.trim() : null;

    boolean hasSparkJob = false;

    String graphType = form.get("select-graph-type");

    // get the graph type
    if (graphType == null) {
      graphType = "resources";
    }

    if (!Utils.isSet(partialFlowDefId)) {
      if (version.equals(Version.NEW)) {
        return ok(flowHistoryPage
            .render(partialFlowDefId, graphType, flowHistoryResults.render(null, null, null, null)));
      } else {
        return ok(
            oldFlowHistoryPage.render(partialFlowDefId, graphType, oldFlowHistoryResults.render(null, null, null, null)));
      }
    }

    IdUrlPair flowDefPair = bestSchedulerInfoMatchGivenPartialId(partialFlowDefId, AppResult.TABLE.FLOW_DEF_ID);

    List<AppResult> results;

    if (graphType.equals("time") || graphType.equals("resources")) {

      // if graph type is time or resources, we don't need the result from APP_HEURISTIC_RESULTS
      results = AppResult.find.select(
          AppResult.getSearchFields() + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL + ","
              + AppResult.TABLE.JOB_DEF_ID + "," + AppResult.TABLE.JOB_DEF_URL + "," + AppResult.TABLE.JOB_NAME)
          .where()
          .eq(AppResult.TABLE.FLOW_DEF_ID, flowDefPair.getId())
          .order()
          .desc(AppResult.TABLE.FINISH_TIME)
          .setMaxRows(JOB_HISTORY_LIMIT)
          .findList();
    } else {

      // Fetch available flow executions with latest JOB_HISTORY_LIMIT mr jobs.
      results = AppResult.find.select(
          AppResult.getSearchFields() + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL + ","
              + AppResult.TABLE.JOB_DEF_ID + "," + AppResult.TABLE.JOB_DEF_URL + "," + AppResult.TABLE.JOB_NAME)
          .where()
          .eq(AppResult.TABLE.FLOW_DEF_ID, flowDefPair.getId())
          .order()
          .desc(AppResult.TABLE.FINISH_TIME)
          .setMaxRows(JOB_HISTORY_LIMIT)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, AppHeuristicResult.getSearchFields())
          .findList();
    }
    if (results.size() == 0) {
      return notFound("Unable to find record for flow def id: " + flowDefPair.getId());
    }

    for (AppResult result : results) {
      if (result.jobType.equals("Spark")) {
        hasSparkJob = true;
      }
    }

    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap = ControllerUtil
        .limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID),
            results.size(), MAX_HISTORY_LIMIT);

    // Compute flow execution data
    List<AppResult> filteredResults = new ArrayList<AppResult>();     // All jobs starting from latest execution
    List<Long> flowExecTimeList = new ArrayList<Long>();         // To map executions to resp execution time
    Map<IdUrlPair, Map<IdUrlPair, List<AppResult>>> executionMap =
        new LinkedHashMap<IdUrlPair, Map<IdUrlPair, List<AppResult>>>();
    for (Map.Entry<IdUrlPair, List<AppResult>> entry : flowExecIdToJobsMap.entrySet()) {

      // Reverse the list content from desc order of finish time to increasing order so that when grouping we get
      // the job list in the order of completion.
      List<AppResult> mrJobsList = Lists.reverse(entry.getValue());

      // Flow exec time is the finish time of the last mr job in the flow
      flowExecTimeList.add(mrJobsList.get(mrJobsList.size() - 1).finishTime);

      filteredResults.addAll(mrJobsList);
      executionMap.put(entry.getKey(), ControllerUtil.groupJobs(mrJobsList, ControllerUtil.GroupBy.JOB_DEFINITION_ID));
    }

    // Calculate unique list of jobs (job def url) to maintain order across executions. List will contain job def urls
    // from latest execution first followed by any other extra job def url that may appear in previous executions.
    final Map<IdUrlPair, String> idPairToJobNameMap = new ListOrderedMap() ;

    Map<IdUrlPair, List<AppResult>> filteredTempMap =
        ControllerUtil.groupJobs(filteredResults, ControllerUtil.GroupBy.JOB_DEFINITION_ID);

    List<Map.Entry<IdUrlPair, List<AppResult>>> filteredMapList =
        new LinkedList<Map.Entry<IdUrlPair, List<AppResult>>>( filteredTempMap.entrySet() );

    Collections.sort(filteredMapList, new Comparator<Map.Entry<IdUrlPair, List<AppResult>>>() {
      @Override
      public int compare(Map.Entry<IdUrlPair, List<AppResult>> idUrlPairListMap, Map.Entry<IdUrlPair, List<AppResult>> t1) {
        return ( new Long(idUrlPairListMap.getValue().get(0).finishTime)).compareTo(t1.getValue().get(0).finishTime);
      }
    });


    for (Map.Entry<IdUrlPair, List<AppResult>> entry : filteredMapList) {
      idPairToJobNameMap.put(entry.getKey(), entry.getValue().get(0).jobName);
    }

    if (version.equals(Version.NEW)) {
      if (graphType.equals("heuristics")) {
        return ok(flowHistoryPage.render(flowDefPair.getId(), graphType,
            flowHistoryResults.render(flowDefPair, executionMap, idPairToJobNameMap, flowExecTimeList)));
      } else if (graphType.equals("resources") || graphType.equals("time")) {
          return ok(flowHistoryPage.render(flowDefPair.getId(), graphType, flowMetricsHistoryResults
              .render(flowDefPair, graphType, executionMap, idPairToJobNameMap, flowExecTimeList)));
      }
    } else {
      if (graphType.equals("heuristics")) {
        return ok(oldFlowHistoryPage.render(flowDefPair.getId(), graphType,
            oldFlowHistoryResults.render(flowDefPair, executionMap, idPairToJobNameMap, flowExecTimeList)));
      } else if (graphType.equals("resources") || graphType.equals("time")) {
        if (hasSparkJob) {
          return notFound("Cannot plot graph for " + graphType + " since it contains a spark job. " + graphType
              + " graphs are not supported for spark right now");
        } else {
          return ok(oldFlowHistoryPage.render(flowDefPair.getId(), graphType, oldFlowMetricsHistoryResults
                  .render(flowDefPair, graphType, executionMap, idPairToJobNameMap, flowExecTimeList)));
        }
      }
    }
    return notFound("Unable to find graph type: " + graphType);
  }

  /**
   * Controls Job History. Displays at max MAX_HISTORY_LIMIT executions. Old version of the job history
   */
  public static Result oldJobHistory() {
    return getJobHistory(Version.OLD);
  }

  /**
   * Controls Job History. Displays at max MAX_HISTORY_LIMIT executions. New version of the job history
   */
  public static Result jobHistory() {
    return getJobHistory(Version.NEW);
  }

  /**
   * Returns the job history. Returns at max MAX_HISTORY_LIMIT executions.
   *
   * @param version The version of job history to return
   * @return The job history page based on the version.
   */
  private static Result getJobHistory(Version version) {
    DynamicForm form = Form.form().bindFromRequest(request());
    String partialJobDefId = form.get(JOB_DEF_ID);
    partialJobDefId = (partialJobDefId != null) ? partialJobDefId.trim() : null;

    boolean hasSparkJob = false;
    // get the graph type
    String graphType = form.get("select-graph-type");

    if (graphType == null) {
      graphType = "resources";
    }

    if (!Utils.isSet(partialJobDefId)) {
      if (version.equals(Version.NEW)) {
        return ok(
            jobHistoryPage.render(partialJobDefId, graphType, jobHistoryResults.render(null, null, -1, null)));
      } else {
        return ok(oldJobHistoryPage.render(partialJobDefId, graphType, oldJobHistoryResults.render(null, null, -1, null)));
      }
    }
    IdUrlPair jobDefPair = bestSchedulerInfoMatchGivenPartialId(partialJobDefId, AppResult.TABLE.JOB_DEF_ID);

    List<AppResult> results;

    if (graphType.equals("time") || graphType.equals("resources")) {
      // we don't need APP_HEURISTIC_RESULT_DETAILS data to plot for time and resources
      results = AppResult.find.select(
          AppResult.getSearchFields() + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL)
          .where()
          .eq(AppResult.TABLE.JOB_DEF_ID, jobDefPair.getId())
          .order()
          .desc(AppResult.TABLE.FINISH_TIME)
          .setMaxRows(JOB_HISTORY_LIMIT)
          .findList();
    } else {
      // Fetch all job executions
      results = AppResult.find.select(
          AppResult.getSearchFields() + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL)
          .where()
          .eq(AppResult.TABLE.JOB_DEF_ID, jobDefPair.getId())
          .order()
          .desc(AppResult.TABLE.FINISH_TIME)
          .setMaxRows(JOB_HISTORY_LIMIT)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
          .findList();
    }

    for (AppResult result : results) {
      if (result.jobType.equals("Spark")) {
        hasSparkJob = true;
      }
    }

    if (results.size() == 0) {
      return notFound("Unable to find record for job def id: " + jobDefPair.getId());
    }
    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap = ControllerUtil
        .limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID),
            results.size(), MAX_HISTORY_LIMIT);

    // Compute job execution data
    List<Long> flowExecTimeList = new ArrayList<Long>();
    int maxStages = 0;
    Map<IdUrlPair, List<AppResult>> executionMap = new LinkedHashMap<IdUrlPair, List<AppResult>>();
    for (Map.Entry<IdUrlPair, List<AppResult>> entry : flowExecIdToJobsMap.entrySet()) {

      // Reverse the list content from desc order of finish time to increasing order so that when grouping we get
      // the job list in the order of completion.
      List<AppResult> mrJobsList = Lists.reverse(entry.getValue());

      // Get the finish time of the last mr job that completed in current flow.
      flowExecTimeList.add(mrJobsList.get(mrJobsList.size() - 1).finishTime);

      // Find the maximum number of mr stages for any job execution
      int stageSize = flowExecIdToJobsMap.get(entry.getKey()).size();
      if (stageSize > maxStages) {
        maxStages = stageSize;
      }

      executionMap.put(entry.getKey(), Lists.reverse(flowExecIdToJobsMap.get(entry.getKey())));
    }
    if (maxStages > STAGE_LIMIT) {
      maxStages = STAGE_LIMIT;
    }
    if (version.equals(Version.NEW)) {
      if (graphType.equals("heuristics")) {
        return ok(jobHistoryPage.render(jobDefPair.getId(), graphType,
            jobHistoryResults.render(jobDefPair, executionMap, maxStages, flowExecTimeList)));
      } else if (graphType.equals("resources") || graphType.equals("time")) {
          return ok(jobHistoryPage.render(jobDefPair.getId(), graphType,
              jobMetricsHistoryResults.render(jobDefPair, graphType, executionMap, maxStages, flowExecTimeList)));
      }
    } else {
      if (graphType.equals("heuristics")) {
        return ok(oldJobHistoryPage.render(jobDefPair.getId(), graphType,
            oldJobHistoryResults.render(jobDefPair, executionMap, maxStages, flowExecTimeList)));
      } else if (graphType.equals("resources") || graphType.equals("time")) {
        if (hasSparkJob) {
          return notFound("Resource and time graph are not supported for spark right now");
        } else {
          return ok(oldJobHistoryPage.render(jobDefPair.getId(), graphType,
              oldJobMetricsHistoryResults.render(jobDefPair, graphType, executionMap, maxStages, flowExecTimeList)));
        }
      }
    }
    return notFound("Unable to find graph type: " + graphType);
  }

  /**
   * Returns the help based on the version
   *
   * @param version The version for which help page has to be returned
   * @return The help page based on the version
   */
  private static Result getHelp(Version version) {
    DynamicForm form = Form.form().bindFromRequest(request());
    String topic = form.get("topic");
    Html page = null;
    String title = "Help";
    if (topic != null && !topic.isEmpty()) {
      // check if it is a heuristic help
      page = ElephantContext.instance().getHeuristicToView().get(topic);

      // check if it is a metrics help
      if (page == null) {
        page = getMetricsNameView().get(topic);
      }

      if (page != null) {
        title = topic;
      }
    }

    if (version.equals(Version.NEW)) {
      return ok(helpPage.render(title, page));
    }
    return ok(oldHelpPage.render(title, page));
  }

  /**
   * Controls the new Help Page
   */
  public static Result oldHelp() {
    return getHelp(Version.OLD);
  }

  /**
   * Controls the old Help Page
   */
  public static Result help() {
    return getHelp(Version.NEW);
  }


  private static Map<String, Html> getMetricsNameView() {
    Map<String,Html> metricsViewMap = new HashMap<String, Html>();
    metricsViewMap.put(Metrics.RUNTIME.getText(), helpRuntime.render());
    metricsViewMap.put(Metrics.WAIT_TIME.getText(), helpWaittime.render());
    metricsViewMap.put(Metrics.USED_RESOURCES.getText(), helpUsedResources.render());
    metricsViewMap.put(Metrics.WASTED_RESOURCES.getText(), helpWastedResources.render());
    return metricsViewMap;
  }
  /**
   * Parse the string for time in long
   *
   * @param time The string to be parsed
   * @return the epoch value
   */
  private static long parseTime(String time) {
    long unixTime = 0;
    try {
      unixTime = Long.parseLong(time);
    } catch (NumberFormatException ex) {
      // return 0
    }
    return unixTime;
  }

  /**
   * Rest API for searching a particular job information
   * E.g, localhost:8080/rest/job?id=xyz
   */
  public static Result restAppResult(String id) {

    if (id == null || id.isEmpty()) {
      return badRequest("No job id provided.");
    }
    if (id.contains("job")) {
      id = id.replaceAll("job", "application");
    }

    AppResult result = AppResult.find.select("*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
        .where()
        .idEq(id)
        .findUnique();

    if (result != null) {
      return ok(Json.toJson(result));
    } else {
      return notFound("Unable to find record on id: " + id);
    }
  }

  /**
   * Rest API for searching a particular job information
   * E.g, localhost:8080/rest/job?id=xyz
   */
  public static Result getTuningJob(String id) {
          JobCompleteDetector jobCompleteDetector = new JobCompleteDetector();
          try{
              List<JobExecution> completedJobExecution = jobCompleteDetector.updateCompletedJobs();
              if(completedJobExecution!=null) {
                  return ok(Json.toJson(completedJobExecution));
              }else{
                  return notFound("Null response");
              }
          } catch (Exception e){
              return notFound(e.toString());
          }

  }

//    if (id == null || id.isEmpty()) {
//      return badRequest("No job id provided.");
//    }
//
//    //    try{
//    //      AzkabanWorkflowClient azkabanWorkflowClient=new AzkabanWorkflowClient("https://ltx1-holdemaz01.grid.linkedin.com:8443/executor?execid=4567967");
//    //      azkabanWorkflowClient.login("ro_elephant_az_svc", new File("/Users/mkumar1/hguest.private_key.der"));
//    //    }catch(Exception e)
//    //    {
//    //      logger.error("Error in log " , e);
//    //    }
//
//    JobCompleteDetector jobCompleteDetector = new JobCompleteDetector();
//    FitnessComputeUtil fitnessComputeUtil = new FitnessComputeUtil();
//    List<JobExecution> jobExecutions = null;
//    try {
//      switch (id) {
//        case "1":
//          logger.error("100 ID value is " + id);
//          jobExecutions = jobCompleteDetector.getJobExecution();
//          break;
//        case "2":
//          logger.error("100 ID value is " + id);
//          jobExecutions = jobCompleteDetector.getJobExecution();
//          jobExecutions = jobCompleteDetector.getCompletedJob(jobExecutions);
//          break;
//        case "3":
//          logger.error("100 ID value is " + id);
//          jobExecutions = jobCompleteDetector.updateCompletedJobs();
//          break;
//        case "4":
//          logger.error("100 ID value is " + id);
//          jobExecutions = fitnessComputeUtil.getJobExecution();
//          break;
//        case "5":
//          logger.error("100 ID value is " + id);
//          jobExecutions = fitnessComputeUtil.getJobExecution();
//          fitnessComputeUtil.updateJobMetrics(jobExecutions);
//          break;
//        case "6":
//          logger.error("100 ID value is " + id);
//          return callGetCurrentRunParameters();
//        case "7":
//          logger.error("100 ID value is " + id);
//          return callGetCurrentRunParameters1();
//        default:
//          Job job = Job.find.select("*").where().idEq(id).findUnique();
//          if (job != null) {
//            return ok(Json.toJson(job));
//          } else {
//            return notFound("Unable to find job on id: " + id);
//          }
//      }
//    } catch (Exception e) {
//      logger.error("Error in log ", e);
//      e.printStackTrace();
//      logger.error("Error " + e.getStackTrace().toString());
//    }
//
//    //JobSuggestedParamValue jobSuggestedParamValue=JobSuggestedParamValue.find.select("*").where()
//
//    if (jobExecutions != null) {
//      return ok(Json.toJson(jobExecutions));
//    } else {
//      return notFound("Unable to find job on id: " + id);
//    }
//  }

  public static Result callGetCurrentRunParameters()
  {

    Map<String, String> paramValueMap=new HashMap<String, String>();
    paramValueMap.put("mapreduce.task.io.sort.mb", "10000");
    paramValueMap.put("mapreduce.map.memory.mb", "20000");
    paramValueMap.put("pig.maxCombinedSplitSize", "30000");

    String defaultParams=Json.toJson(paramValueMap).toString();
    String projectName="metricsV2";
    String flowDefId= "https://ltx1-holdemaz03.grid.linkedin.com:8443/manager?project=metricsV2&flow=sitespeed_v2_hourly_finish";
    String jobDefId="https://ltx1-holdemaz03.grid.linkedin.com:8443/manager?project=metricsV2&flow=sitespeed_v2_hourly_finish&job=sitespeed_v2_hourly_datafile_sitespeed_neptune_metrics_new";
    String flowExecId="https://ltx1-holdemaz03.grid.linkedin.com:8443/executor?execid=2592424";
    String jobExecId="https://ltx1-holdemaz03.grid.linkedin.com:8443/executor?execid=2592424&job=sitespeed_v2_hourly_datafile_sitespeed_neptune_metrics_new";
    String client="UMP";
    String userName="mkumar1";
    Boolean isRetry=false;
    Boolean skipExecutionForOptimization=false;
    return getCurrentRunParameters(projectName, flowDefId, jobDefId, flowExecId, jobExecId, defaultParams, client, userName, isRetry, skipExecutionForOptimization);
  }
  public static Result callGetCurrentRunParameters1()
  {

    Map<String, String> paramValueMap=new HashMap<String, String>();
    paramValueMap.put("mapreduce.task.io.sort.mb", "10000");
    paramValueMap.put("mapreduce.map.memory.mb", "20000");
    paramValueMap.put("pig.maxCombinedSplitSize", "30000");

    String defaultParams=Json.toJson(paramValueMap).toString();
    String projectName="metricsV2";
    String flowDefId= "https://ltx1-holdemaz03.grid.linkedin.com:8443/manager?project=metricsV2&flow=sitespeed_v2_hourly_finish";
    String jobDefId="https://ltx1-holdemaz03.grid.linkedin.com:8443/manager?project=metricsV2&flow=sitespeed_v2_hourly_finish&job=sitespeed_v2_hourly_datafile_sitespeed_neptune_metrics_new";
    String flowExecId="https://ltx1-holdemaz03.grid.linkedin.com:8443/executor?execid=2592426";
    String jobExecId="https://ltx1-holdemaz03.grid.linkedin.com:8443/executor?execid=2592426&job=sitespeed_v2_hourly_datafile_sitespeed_neptune_metrics_new";
    String client="UMP";
    String userName="mkumar1";
    Boolean isRetry=false;
    Boolean skipExecutionForOptimization=false;
    return getCurrentRunParameters(projectName, flowDefId, jobDefId, flowExecId, jobExecId, defaultParams, client, userName, isRetry, skipExecutionForOptimization);
  }

  public static Result getCurrentRunParametersNew()
  {
    String jsonString = request().body().asJson().toString();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> paramValueMap = null;
    try {
      paramValueMap = (Map<String, String>) mapper.readValue(jsonString, Map.class);
    } catch (JsonParseException e) {
      logger.error("Error is " + e);
    } catch (JsonMappingException e) {
      logger.error("Error is " + e);
    } catch (IOException e) {
      logger.error("Error is " + e);
    }

    String defaultParams=paramValueMap.get("defaultParams");
    String projectName=paramValueMap.get("projectName");;
    String flowDefId= paramValueMap.get("flowDefId");
    String jobDefId=paramValueMap.get("jobDefId");
    String flowExecId=paramValueMap.get("flowExecId");
    String jobExecId=paramValueMap.get("jobExecId");
    String client=paramValueMap.get("client");
    String userName=paramValueMap.get("userName");
    Boolean isRetry=Boolean.parseBoolean(paramValueMap.get("isRetry"));
    Boolean skipExecutionForOptimization=Boolean.parseBoolean(paramValueMap.get("skipExecutionForOptimization"));

    return getCurrentRunParameters(projectName, flowDefId, jobDefId, flowExecId, jobExecId, defaultParams, client, userName, isRetry, skipExecutionForOptimization);
  }

  public static Result getCurrentRunParameters(String projectName, String flowDefId, String jobDefId, String flowExecId, String jobExecId, String defaultParams, String client, String userName, Boolean isRetry, Boolean skipExecutionForOptimization)
  {
    AutoTuningAPIHelper autoTuningAPIHelper=new AutoTuningAPIHelper();
    Map<String, String> outputParams=autoTuningAPIHelper.getCurrentRunParameters(projectName, flowDefId, jobDefId, flowExecId, jobExecId, defaultParams, client, userName, isRetry, skipExecutionForOptimization);
    if(outputParams!=null)
    {
      logger.error("Outupt params " + outputParams);
      return ok(Json.toJson(outputParams));
    }else
    {
      return notFound("Unable to find parameters for given job: " + jobDefId + " and flow: " + flowDefId);
    }
  }
  public static Result restParam(){
      ParamGenerator paramGenerator = new PSOParamGenerator();
//      List<Job> jobsForSwarmSuggestion = paramGenerator.fetchJobsForParamSuggestion();
//      List<TunerState> tunerStateList = paramGenerator.getJobsTunerState(jobsForSwarmSuggestion);
//      TunerState tunerState = tunerStateList.get(0);
//
//      TunerState newTunerState = new TunerState();
//      newTunerState.setTuningJob(tunerState.getTuningJob());
//      newTunerState.setParametersToTune(tunerState.getParametersToTune());
//      newTunerState.setStringTunerState("temp");
//
//
//      JsonNode jsonTmp = Json.parse(tmp);
//      JsonNode jsonTunerState = Json.toJson(tunerState);
//      JsonNode stringTunerState = jsonTunerState.get("stringTunerState");
//
//      String parametersToTune = jsonTunerState.get("parametersToTune").toString();
//      String errorString = "Error string";
//      try{
//          Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python /home/aragrawa/development/production/dr-elephant/app/com/linkedin/drelephant/tunin/pso_param_generation.py " +tmp+" "+parametersToTune);
//          BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
//          BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//          String updatedStringTunerState = in.readLine();
//          errorString = error.readLine();
//          newTunerState.setStringTunerState(updatedStringTunerState);
//      } catch (IOException e){
//          return ok("Error:\n\n" + e.toString());
//      }
//
//      return ok(errorString+ "\n\n" + tmp + "\n\n" + jsonTmp + "\n\n" + stringTunerState + "\n\n" + parametersToTune + "\n\n" + Json.toJson(newTunerState));

    paramGenerator.getParams();
    return ok("check");


      ////      TunerState newTunerState = paramGenerator.generateParamSet(tunerState);
//      return ok(Json.toJson(tunerState));

//      List<Job> jobsForSwarmSuggestion = paramGenerator.fetchJobsForParamSuggestion();
//      Job job = jobsForSwarmSuggestion.get(0);
//      List<AlgoParam> algoParamList = AlgoParam.find.where().eq("algo", job.algo).findList();
//      TunerState tunerState = new TunerState();
//      tunerState.setTuningJob(job);
//      tunerState.setParametersToTune(algoParamList);
//
//      JobSavedState jobSavedState = JobSavedState.find.byId(job.jobId);
//      if(jobSavedState!=null){
//
//          String savedState = new String(jobSavedState.savedState);
//          ObjectNode jsonSavedState = (ObjectNode) Json.parse(savedState);
//          JsonNode jsonCurrentPopulation = jsonSavedState.get("current_population");
//          List<Particle> currentPopulation = paramGenerator.jsonToParticleList(jsonCurrentPopulation);
//          for( Particle particle: currentPopulation){
//              Long paramSetId = particle.getParamSetId();
//              JobExecution jobExecution = JobExecution.find.byId(paramSetId);
//              particle.setFitness(jobExecution.costMetric);
//          }
//
//          JsonNode updatedJsonCurrentPopulation = paramGenerator.particleListToJson(currentPopulation);
//          jsonSavedState.set("current_population", updatedJsonCurrentPopulation);
//          savedState = Json.stringify(jsonSavedState);
//          tunerState.setStringTunerState(savedState);
//
//      }
//      else{
//          tunerState.setStringTunerState("{}");
//      }
//
//      return ok(Json.toJson(jobSavedState) + "\n\n");



//      List<TunerState> jobTunerStateList= paramGenerator.getJobsTunerState(jobsForSwarmSuggestion);
//      TunerState tunerState = jobTunerStateList.get(0);
//      TunerState newTunerState = paramGenerator.generateParamSet(tunerState);
//      return ok(Json.toJson(tunerState) + "\n\n" + Json.toJson(newTunerState) );

      //      JsonNode jsonTunerState = Json.toJson(tunerState);
//      JsonNode stringTunerState = jsonTunerState.get("stringTunerState");
////      String stringTunerState = jsonTunerState.get("stringTunerState").toString();
//      String parametersToTune = jsonTunerState.get("parametersToTune").toString();
//      return ok(stringTunerState + "\n\n" + parametersToTune);

      //      TunerState newTunerState = paramGenerator.generateParamSet(tunerState);

//      Job job = newTunerState.getTuningJob();
//      List<AlgoParam> paramList = newTunerState.getParametersToTune();
//      String stringTunerState = newTunerState.getStringTunerState();
//      return ok(Json.toJson(newTunerState));
      //      JsonNode jsonTunerState = Json.parse(stringTunerState);

//      if (jsonTunerState == null){
//          return ok("String tuner state\n\n" + stringTunerState);
//      } else{
//          return ok(jsonTunerState);
//      }



//      List<Job> jobsForSwarmSuggestion = paramGenerator.fetchJobsForParamSuggestion();
//      List<TunerState> jobTunerStateList= paramGenerator.getJobsTunerState(jobsForSwarmSuggestion);
//      TunerState tunerState = jobTunerStateList.get(0);
//      TunerState jobTunerState = paramGenerator.generateParamSet(tunerState);
//
//      Job job = jobTunerState.getTuningJob();
//      List<AlgoParam> paramList = jobTunerState.getParametersToTune();
//      String stringTunerState = jobTunerState.getStringTunerState();
//      JsonNode jsonTunerState = Json.parse(stringTunerState);
//      JsonNode jsonSuggestedPopulation = jsonTunerState.get("current_population");

//      String tmp = "[{\"_candidate\":[1000,1000,5,0.8,4096.0,512.0],\"maximize\":false,\"birthdate\":1.510632948412705E9,\"fitness\":0},{\"_candidate\":[377.649000415422,500.1012005037423,120.9206674491925,0.7929829408557095,2621.991988445575,512.0],\"maximize\":false,\"birthdate\":1.510632948412707E9,\"fitness\":0},{\"_candidate\":[244.18253123845352,1247.5360424531382,113.20897919691934,0.8265120097448186,5660.186827441222,512.0],\"maximize\":false,\"birthdate\":1.510632948412708E9,\"fitness\":0}]";
//      JsonNode jsonSuggestedPopulation = Json.parse(tmp);
//      List<Particle> suggestedPopulation = paramGenerator.jsonToParticleList(jsonSuggestedPopulation);
//
//      return ok(Json.toJson(suggestedPopulation) + "\n\n" + Json.toJson(jsonSuggestedPopulation));
//    List<Job> jobsForSwarmSuggestion = paramGenerator.fetchJobsForParamSuggestion();
//    List<TunerState> jobTunerStateList= paramGenerator.getJobsTunerState(jobsForSwarmSuggestion);
//    TunerState tunerState = jobTunerStateList.get(0);
//    JsonNode jsonTunerState = Json.toJson(tunerState);
//    String stringTunerState = jsonTunerState.get("stringTunerState").toString();
//    String parametersToTune = jsonTunerState.get("parametersToTune").toString();
//    try{
//      Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python /home/aragrawa/development/production/dr-elephant/app/com/linkedin/drelephant/tunin/pso_param_generation.py " +stringTunerState+" "+parametersToTune);
//      BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
//      String updatedStringTunerState = in.readLine();
//      tunerState.setStringTunerState(updatedStringTunerState);
//      if(updatedStringTunerState!=null){
//        return ok(updatedStringTunerState);
//      }
//    } catch (IOException e){
//      System.out.println(e);
//    }
//    return ok(stringTunerState + "\n\n" + parametersToTune);



//    List<TunerState> tunerStateList = paramGenerator.ParamGenerator();
//    if(tunerStateList != null){
//      return ok(Json.toJson(tunerStateList));
//    }
//    return notFound("Tuner state list is null");
//    List<Job> jobsForSwarmSuggestion = paramGenerator.fetchJobsForParamSuggestion();
//    List<TunerState> jobTunerStateList= paramGenerator.getJobsTunerState(jobsForSwarmSuggestion);
//
//    TunerState tunerState = jobTunerStateList.get(0);
//
//    JsonNode jsonTunerState = Json.toJson(tunerState);
//    String stringTunerState = jsonTunerState.get("stringTunerState").toString();
//    String parametersToTune = jsonTunerState.get("parametersToTune").toString();
//
//
//    try{
//      Process p = Runtime.getRuntime().exec("/home/aragrawa/virtualenvs/auto-tuning/bin/python /home/aragrawa/development/aragrawas-hadoop-tuning/hadoop-tuning/src/linkedin/restartable/pso_param_generation.py " +stringTunerState+ " " +parametersToTune);
//      BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
//      String updatedStringTunerState = in.readLine();
//      tunerState.setStringTunerState(updatedStringTunerState);
//      if(updatedStringTunerState != null) {
//        return ok(Json.toJson(updatedStringTunerState));
//      }else{
//        return ok(stringTunerState + "\n\n" + parametersToTune);
//      }
//    } catch (IOException e){
//      System.out.println(e);
//      return notFound(e.toString());
//    }


//    return notFound("Not found");


//    if(stringTunerState!=null){
//      return ok(jsonTunerState + "\n\n" + stringTunerState + "\n\n" + parametersToTune);
//    }else{
//      return ok(jsonTunerState);
//    }


//    Particle particle = new Particle();
//
//    particle.setBirthdate(1);
//    particle.setFitness(2);
//    particle.setMaximize(false);
//    particle.setPramSetId((long)3);
//    List<Double> candidate = new ArrayList<Double>();
//    candidate.add((double)4);
//    particle.setCandidate(candidate);
//
//    JsonNode jsonParticle = Json.toJson(particle);
//
//    Particle newparticle = Json.fromJson(jsonParticle, Particle.class);

//    List<Job> jobList = paramGenerator.fetchJobsForParamSuggestion();
//    Job job = jobList.get(0);
//    JobSavedState jobSavedState = JobSavedState.find.byId(job.jobId);
//    String savedState = new String(jobSavedState.savedState);
//
//    ObjectNode jsonSavedState = (ObjectNode) Json.parse(savedState);
//    JsonNode jsonCurrentPopulation = jsonSavedState.get("current_population");
//
//    return ok(jsonParticle + "\n\n" + Json.toJson(newparticle));
//    List<Particle> currentPopulation = paramGenerator.jsonToParticleList(jsonCurrentPopulation);
//
//
//    return ok(jsonCurrentPopulation + "\n\n" + Json.toJson(currentPopulation));
//    List<TunerState> tunerStateList= paramGenerator.getJobsTunerState(jobList);
//    if(tunerStateList!= null){
//      TunerState tunerState = tunerStateList.get(0);
//      String state =tunerState.getStringTunerState();
//      return ok(Json.parse(state));
//    }
//    else{
//      return notFound(Json.toJson(jobList));
//    }


//    List<AlgoParam> paramList = tunerState.getParametersToTune();
//
//    String state = "{\"current_population\": [{\"_candidate\": [35.08818788666294, 7.749754445944973], \"maximize\": false, \"birthdate\": 1510288289.837573, \"fitness\": 1291.239623142204}, {\"_candidate\": [3.199917189984381, -10.769648835738517], \"maximize\": false, \"birthdate\": 1510288289.837574, \"fitness\": 126.22480606788152}, {\"_candidate\": [1.9565303311484934, 7.447017820592105], \"maximize\": false, \"birthdate\": 1510288289.837575, \"fitness\": 59.28608535692042}], \"prev_population\": [{\"_candidate\": [-19.27348244949365, -13.757291957914351], \"maximize\": false, \"birthdate\": 1510288289.837448, \"fitness\": 560.7302077462346}, {\"_candidate\": [6.476813168476025, 10.655249883797493], \"maximize\": false, \"birthdate\": 1510288289.837449, \"fitness\": 155.48345890551093}, {\"_candidate\": [1.8237963003244935, 7.897503085567713], \"maximize\": false, \"birthdate\": 1510288289.83745, \"fitness\": 65.69678793162886}], \"archive\": [{\"_candidate\": [13.222154663549407, -17.453153020563732], \"maximize\": false, \"birthdate\": 1510288289.837319, \"fitness\": 479.4379243060343}, {\"_candidate\": [1.6702614733455174, -0.9326084010362266], \"maximize\": false, \"birthdate\": 1510288289.837321, \"fitness\": 3.659531819025686}, {\"_candidate\": [1.9565303311484934, 7.447017820592105], \"maximize\": false, \"birthdate\": 1510288289.837575, \"fitness\": 59.28608535692042}]}";
//    tunerState.setStringTunerState(state);
//    tunerStateList.set(0, tunerState);


//    String stringTunerState = tunerState.getStringTunerState();
//    JsonNode jsonTunerState = Json.parse(stringTunerState);
//    JsonNode jsonSuggestedPopulation = jsonTunerState.get("current_population");
//
//    List<Particle> suggestedPopulation =  paramGenerator.jsonToParticleList(jsonSuggestedPopulation);
//    Particle particle = suggestedPopulation.get(0);
//
//    List<JobSuggestedParamValue> jobSuggestedParamValueList = paramGenerator.getParamValueList(particle, paramList);
//
//    Job job = tunerState.getTuningJob();
//    JobExecution jobExecution = new JobExecution();
//    jobExecution.job = job;
//    jobExecution.algo = job.algo;
//    jobExecution.isDefaultExecution = false;
//    if (paramGenerator.isParamConstraintViolated(jobSuggestedParamValueList)){
//      jobExecution.paramSetState = JobExecution.ParamSetStatus.FITNESS_COMPUTED;
//      jobExecution.resourceUsage = (double) -1;
//      jobExecution.executionTime = (double) -1;
//      jobExecution.costMetric = 3 * job.averageResourceUsage * job.allowedMaxResourceUsagePercent / 100;
//    }
//    else{
//      jobExecution.paramSetState = JobExecution.ParamSetStatus.CREATED;
//    }
//    Long paramSetId = paramGenerator.saveSuggestedParamMetadata(jobExecution);
//
//    for (JobSuggestedParamValue jobSuggestedParamValue: jobSuggestedParamValueList){
//      jobSuggestedParamValue.jobExecution = jobExecution;
//      jobSuggestedParamValue.paramValuePK.primaryKeyParamSetId = paramSetId;
//    }
//
//    particle.setPramSetId(paramSetId);
//    paramGenerator.saveSuggestedParams(jobSuggestedParamValueList);

//    Boolean penalty =  paramGenerator.isParamConstraintViolated(jobSuggestedParamValueList);

//    if(paramSetId != null){
//      return ok(Json.toJson(particle) + "\n\n" + Json.toJson(jobSuggestedParamValueList));
//    } else{
//      return ok(Json.toJson(jobExecution));
//    }

//    paramGenerator.updateDatabase(tunerStateList);
//    return ok("Check");

//    Job job = jobList.get(0);
//    JobExecution jobExecution = new JobExecution();
//    jobExecution.job = job;
//    jobExecution.algo = job.algo;
//    jobExecution.isDefaultExecution = false;
//    jobExecution.paramSetState = JobExecution.ParamSetStatus.DISCARDED;
//
//    Long num = paramGenerator.saveSuggestedParamMetadata(jobExecution);
//    if(num!=null){
//      return ok(Json.toJson(num));
//    } else{
//      return notFound("Null return");
//    }
//    List<Particle> particleList = new ArrayList<Particle>();
//    Particle particle = new Particle();
//    List<Float> floatList = new ArrayList<Float>();
//    floatList.add((float) 1.2);
//    floatList.add((float) 1.4);
//    particle.setCandidate(floatList);
//    particle.setFitness((float) 1.6);
//    particle.setBirthdate((double) 1.8);
//    particle.setMaximize(false);
////    particle.setPramSetId((long) 1);
//    particleList.add(particle);
//    JsonNode json = paramGenerator.particleListToJson(particleList);
//
//    particleList = paramGenerator.jsonToParticleList(json);
//    return ok(json);
  }



  /**
   * Rest API for searching all jobs triggered by a particular Scheduler Job
   * E.g., localhost:8080/rest/jobexec?id=xyz
   */
  public static Result restJobExecResult(String jobExecId) {

    if (jobExecId == null || jobExecId.isEmpty()) {
      return badRequest("No job exec url provided.");
    }

    List<AppResult> result = AppResult.find.select("*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
        .where()
        .eq(AppResult.TABLE.JOB_EXEC_ID, jobExecId)
        .findList();

    if (result.size() == 0) {
      return notFound("Unable to find record on job exec url: " + jobExecId);
    }

    return ok(Json.toJson(result));
  }

  /**
   * Rest API for searching all jobs under a particular flow execution
   * E.g., localhost:8080/rest/flowexec?id=xyz
   */
  public static Result restFlowExecResult(String flowExecId) {

    if (flowExecId == null || flowExecId.isEmpty()) {
      return badRequest("No flow exec url provided.");
    }

    List<AppResult> results = AppResult.find.select("*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
        .where()
        .eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecId)
        .findList();

    if (results.size() == 0) {
      return notFound("Unable to find record on flow exec url: " + flowExecId);
    }

    Map<IdUrlPair, List<AppResult>> groupMap = ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.JOB_EXECUTION_ID);

    Map<String, List<AppResult>> resMap = new HashMap<String, List<AppResult>>();
    for (Map.Entry<IdUrlPair, List<AppResult>> entry : groupMap.entrySet()) {
      IdUrlPair jobExecPair = entry.getKey();
      List<AppResult> value = entry.getValue();
      resMap.put(jobExecPair.getId(), value);
    }

    return ok(Json.toJson(resMap));
  }





  /**
   * The Rest API for Search Feature
   *
   * http://localhost:8080/rest/search?username=abc&job-type=HadoopJava
   */
  public static Result restSearch() {
    DynamicForm form = Form.form().bindFromRequest(request());
    String appId = form.get(APP_ID);
    appId = appId != null ? appId.trim() : "";
    if (appId.contains("job")) {
      appId = appId.replaceAll("job", "application");
    }
    String flowExecId = form.get(FLOW_EXEC_ID);
    flowExecId = (flowExecId != null) ? flowExecId.trim() : null;
    if (!appId.isEmpty()) {
      AppResult result = AppResult.find.select("*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
              "*")
          .where()
          .idEq(appId)
          .findUnique();
      if (result != null) {
        return ok(Json.toJson(result));
      } else {
        return notFound("Unable to find record on id: " + appId);
      }
    } else if (flowExecId != null && !flowExecId.isEmpty()) {
      List<AppResult> results = AppResult.find.select("*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
              "*")
          .where()
          .eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecId)
          .findList();
      if (results.size() == 0) {
        return notFound("Unable to find record on flow execution: " + flowExecId);
      } else {
        return ok(Json.toJson(results));
      }
    }

    int page = 1;
    if (request().queryString().containsKey(PAGE)) {
      page = Integer.parseInt(request().queryString().get(PAGE)[0]);
      if (page <= 0) {
        page = 1;
      }
    }

    Query<AppResult> query = generateSearchQuery("*", getSearchParams());
    List<AppResult> results = query.setFirstRow((page - 1) * REST_PAGE_LENGTH)
        .setMaxRows(REST_PAGE_LENGTH)
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
        .findList();

    if (results.size() == 0) {
      return notFound("No records");
    } else {
      return ok(Json.toJson(results));
    }
  }

  /**
   * The Rest API for Compare Feature
   * E.g., localhost:8080/rest/compare?flow-exec-id1=abc&flow-exec-id2=xyz
   */
  public static Result restCompare() {
    DynamicForm form = Form.form().bindFromRequest(request());
    String flowExecId1 = form.get(COMPARE_FLOW_ID1);
    flowExecId1 = (flowExecId1 != null) ? flowExecId1.trim() : null;
    String flowExecId2 = form.get(COMPARE_FLOW_ID2);
    flowExecId2 = (flowExecId2 != null) ? flowExecId2.trim() : null;

    List<AppResult> results1 = null;
    List<AppResult> results2 = null;
    if (flowExecId1 != null && !flowExecId1.isEmpty() && flowExecId2 != null && !flowExecId2.isEmpty()) {
      results1 = AppResult.find.select("*")
          .where()
          .eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecId1)
          .setMaxRows(100)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
              "*")
          .findList();
      results2 = AppResult.find.select("*")
          .where()
          .eq(AppResult.TABLE.FLOW_EXEC_ID, flowExecId2)
          .setMaxRows(100)
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
          .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS,
              "*")
          .findList();
    }

    Map<IdUrlPair, Map<IdUrlPair, List<AppResult>>> compareResults = compareFlows(results1, results2);

    Map<String, Map<String, List<AppResult>>> resMap = new HashMap<String, Map<String, List<AppResult>>>();
    for (Map.Entry<IdUrlPair, Map<IdUrlPair, List<AppResult>>> entry : compareResults.entrySet()) {
      IdUrlPair jobExecPair = entry.getKey();
      Map<IdUrlPair, List<AppResult>> value = entry.getValue();
      for (Map.Entry<IdUrlPair, List<AppResult>> innerEntry : value.entrySet()) {
        IdUrlPair flowExecPair = innerEntry.getKey();
        List<AppResult> results = innerEntry.getValue();
        Map<String, List<AppResult>> resultMap = new HashMap<String, List<AppResult>>();
        resultMap.put(flowExecPair.getId(), results);
        resMap.put(jobExecPair.getId(), resultMap);
      }
    }

    return ok(Json.toJson(resMap));
  }

  /**
   * The data for plotting the flow history graph
   *
   * <pre>
   * {@code
   *   [
   *     {
   *       "flowtime": <Last job's finish time>,
   *       "score": 1000,
   *       "jobscores": [
   *         {
   *           "jobdefurl:" "url",
   *           "jobexecurl:" "url",
   *           "jobscore": 500
   *         },
   *         {
   *           "jobdefurl:" "url",
   *           "jobexecurl:" "url",
   *           "jobscore": 500
   *         }
   *       ]
   *     },
   *     {
   *       "flowtime": <Last job's finish time>,
   *       "score": 700,
   *       "jobscores": [
   *         {
   *           "jobdefurl:" "url",
   *           "jobexecurl:" "url",
   *           "jobscore": 0
   *         },
   *         {
   *           "jobdefurl:" "url",
   *           "jobexecurl:" "url",
   *           "jobscore": 700
   *         }
   *       ]
   *     }
   *   ]
   * }
   * </pre>
   */
  public static Result restFlowGraphData(String flowDefId) {
    JsonArray datasets = new JsonArray();
    if (flowDefId == null || flowDefId.isEmpty()) {
      return ok(new Gson().toJson(datasets));
    }

    // Fetch available flow executions with latest JOB_HISTORY_LIMIT mr jobs.
    List<AppResult> results = getRestFlowAppResults(flowDefId);

    if (results.size() == 0) {
      logger.info("No results for Job url");
    }
    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap =
        ControllerUtil.limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID), results.size(), MAX_HISTORY_LIMIT);

    // Compute the graph data starting from the earliest available execution to latest
    List<IdUrlPair> keyList = new ArrayList<IdUrlPair>(flowExecIdToJobsMap.keySet());
    for (int i = keyList.size() - 1; i >= 0; i--) {
      IdUrlPair flowExecPair = keyList.get(i);
      int flowPerfScore = 0;
      JsonArray jobScores = new JsonArray();
      List<AppResult> mrJobsList = Lists.reverse(flowExecIdToJobsMap.get(flowExecPair));
      Map<IdUrlPair, List<AppResult>> jobDefIdToJobsMap = ControllerUtil.groupJobs(mrJobsList, ControllerUtil.GroupBy.JOB_DEFINITION_ID);

      // Compute the execution records. Note that each entry in the jobDefIdToJobsMap will have at least one AppResult
      for (IdUrlPair jobDefPair : jobDefIdToJobsMap.keySet()) {
        // Compute job perf score
        int jobPerfScore = 0;
        for (AppResult job : jobDefIdToJobsMap.get(jobDefPair)) {
          jobPerfScore += job.score;
        }

        // A job in jobscores list
        JsonObject jobScore = new JsonObject();
        jobScore.addProperty("jobscore", jobPerfScore);
        jobScore.addProperty("jobdefurl", jobDefPair.getUrl());
        jobScore.addProperty("jobexecurl", jobDefIdToJobsMap.get(jobDefPair).get(0).jobExecUrl);

        jobScores.add(jobScore);
        flowPerfScore += jobPerfScore;
      }

      // Execution record
      JsonObject dataset = new JsonObject();
      dataset.addProperty("flowtime", mrJobsList.get(mrJobsList.size() - 1).finishTime);
      dataset.addProperty("score", flowPerfScore);
      dataset.add("jobscores", jobScores);

      datasets.add(dataset);
    }

    return ok(new Gson().toJson(datasets));
  }

  /**
   * The data for plotting the job history graph. While plotting the job history
   * graph an ajax call is made to this to fetch the graph data.
   *
   * Data Returned:
   * <pre>
   * {@code
   *   [
   *     {
   *       "flowtime": <Last job's finish time>,
   *       "score": 1000,
   *       "stagescores": [
   *         {
   *           "stageid:" "id",
   *           "stagescore": 500
   *         },
   *         {
   *           "stageid:" "id",
   *           "stagescore": 500
   *         }
   *       ]
   *     },
   *     {
   *       "flowtime": <Last job's finish time>,
   *       "score": 700,
   *       "stagescores": [
   *         {
   *           "stageid:" "id",
   *           "stagescore": 0
   *         },
   *         {
   *           "stageid:" "id",
   *           "stagescore": 700
   *         }
   *       ]
   *     }
   *   ]
   * }
   * </pre>
   */
  public static Result restJobGraphData(String jobDefId) {
    JsonArray datasets = new JsonArray();
    if (jobDefId == null || jobDefId.isEmpty()) {
      return ok(new Gson().toJson(datasets));
    }

    // Fetch available flow executions with latest JOB_HISTORY_LIMIT mr jobs.
    List<AppResult> results = getRestJobAppResults(jobDefId);

    if (results.size() == 0) {
      logger.info("No results for Job url");
    }
    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap =
        ControllerUtil.limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID), results.size(), MAX_HISTORY_LIMIT);

    // Compute the graph data starting from the earliest available execution to latest
    List<IdUrlPair> keyList = new ArrayList<IdUrlPair>(flowExecIdToJobsMap.keySet());
    for (int i = keyList.size() - 1; i >= 0; i--) {
      IdUrlPair flowExecPair = keyList.get(i);
      int jobPerfScore = 0;
      JsonArray stageScores = new JsonArray();
      List<AppResult> mrJobsList = Lists.reverse(flowExecIdToJobsMap.get(flowExecPair));
      for (AppResult appResult : flowExecIdToJobsMap.get(flowExecPair)) {

        // Each MR job triggered by jobDefId for flowExecId
        int mrPerfScore = 0;
        for (AppHeuristicResult appHeuristicResult : appResult.yarnAppHeuristicResults) {
          mrPerfScore += appHeuristicResult.score;
        }

        // A particular mr stage
        JsonObject stageScore = new JsonObject();
        stageScore.addProperty("stageid", appResult.id);
        stageScore.addProperty("stagescore", mrPerfScore);

        stageScores.add(stageScore);
        jobPerfScore += mrPerfScore;
      }

      // Execution record
      JsonObject dataset = new JsonObject();
      dataset.addProperty("flowtime", mrJobsList.get(mrJobsList.size() - 1).finishTime);
      dataset.addProperty("score", jobPerfScore);
      dataset.add("stagescores", stageScores);

      datasets.add(dataset);
    }

    return ok(new Gson().toJson(datasets));
  }

  /**
   * The data for plotting the job history graph using time and resource metrics. While plotting the job history
   * graph an ajax call is made to this to fetch the graph data.
   *
   * Data Returned:
   * <pre>
   * [
   *  {
   *    "flowtime": 1461234105456,
   *    "runtime": 2312107,
   *    "waittime": 118879,
   *    "resourceused": 304934912,
   *    "resourcewasted": 172913,
   *    "jobmetrics": [
   *      {
   *        "stageid": "application_1458194917883_1587177",
   *        "runtime": 642986,
   *        "waittime": 14016,
   *        "resourceused": 277352448,
   *        "resourcewasted": 0
   *    }],
   *  },
   *  {
   *    "flowtime": 1461237538639,
   *    "runtime": 2155354,
   *    "waittime": 112187,
   *    "resourceused": 293096448,
   *    "resourcewasted": 400461,
   *    "jobmetrics": [
   *      {
   *        "stageid": "application_1458194917883_1589302",
   *        "runtime": 548924,
   *        "waittime": 16903,
   *        "resourceused": 266217472,
   *        "resourcewasted": 0
   *      }]
   *  }
   *  ]
   *
   * </pre>
   */
  public static Result restJobMetricsGraphData(String jobDefId) {
    JsonArray datasets = new JsonArray();
    if (jobDefId == null || jobDefId.isEmpty()) {
      return ok(new Gson().toJson(datasets));
    }

    List<AppResult> results = getRestJobAppResults(jobDefId);

    if (results.size() == 0) {
      logger.info("No results for Job url");
    }
    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap =
        ControllerUtil.limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID), results.size(), MAX_HISTORY_LIMIT);

    // Compute the graph data starting from the earliest available execution to latest
    List<IdUrlPair> keyList = new ArrayList<IdUrlPair>(flowExecIdToJobsMap.keySet());
    for (int i = keyList.size() - 1; i >= 0; i--) {
      IdUrlPair flowExecPair = keyList.get(i);
      int jobPerfScore = 0;
      JsonArray stageMetrics = new JsonArray();
      List<AppResult> mrJobsList = Lists.reverse(flowExecIdToJobsMap.get(flowExecPair));

      long totalMemoryUsed = 0;
      long totalMemoryWasted = 0;
      long totalDelay = 0;

      for (AppResult appResult : flowExecIdToJobsMap.get(flowExecPair)) {

        // Each MR job triggered by jobDefId for flowExecId
        int mrPerfScore = 0;

        for (AppHeuristicResult appHeuristicResult : appResult.yarnAppHeuristicResults) {
          mrPerfScore += appHeuristicResult.score;
        }

        // A particular mr stage
        JsonObject stageMetric = new JsonObject();
        stageMetric.addProperty("stageid", appResult.id);
        stageMetric.addProperty("runtime", appResult.finishTime - appResult.startTime);
        stageMetric.addProperty("waittime", appResult.totalDelay);
        stageMetric.addProperty("resourceused", appResult.resourceUsed);
        stageMetric.addProperty("resourcewasted", appResult.resourceWasted);

        stageMetrics.add(stageMetric);
        jobPerfScore += mrPerfScore;
        totalMemoryUsed += appResult.resourceUsed;
        totalMemoryWasted += appResult.resourceWasted;
      }

      // Execution record
      JsonObject dataset = new JsonObject();
      dataset.addProperty("flowtime", mrJobsList.get(mrJobsList.size() - 1).finishTime);
      dataset.addProperty("runtime", Utils.getTotalRuntime(mrJobsList));
      dataset.addProperty("waittime", Utils.getTotalWaittime(mrJobsList));
      dataset.addProperty("resourceused", totalMemoryUsed);
      dataset.addProperty("resourcewasted", totalMemoryWasted);
      dataset.add("jobmetrics", stageMetrics);

      datasets.add(dataset);
    }

    return ok(new Gson().toJson(datasets));
  }

  /**
   *
   * @param startTime - beginning of the time window
   * @param endTime - end of the time window
   * @return Json of resourceUsage data for each user for the given time window
   *    eg. [{"user":"bmr","resourceUsed":168030208,"resourceWasted":27262750},
   *        {"user":"payments","resourceUsed":18432,"resourceWasted":3447},
   *        {"user":"myu","resourceUsed":558211072,"resourceWasted":81573818}]
   */
  public static Result restResourceUsageDataByUser(String startTime, String endTime) {
    try {
      JsonArray datasets = new JsonArray();
      if(startTime.length() != endTime.length() ||
          (startTime.length() != 10 && startTime.length() != 13)) {
        return status(300);
      }
      SimpleDateFormat tf = null ;
      if( startTime.length() == 10 ) {
         tf = new SimpleDateFormat("yyyy-MM-dd");
      }
      else {
        tf = new SimpleDateFormat("yyyy-MM-dd-HH");
      }
      Date start = tf.parse(startTime);
      Date end = tf.parse(endTime);
      Collection<AppResourceUsageData> result = getUserResourceUsage(start, end);

      return ok(new Gson().toJson(result));
    }
    catch(ParseException ex) {
      return status(300,"Invalid datetime format : " + ex.getMessage());
    }
  }


  /**
   * Rest data to plot flot history graph using time and resource metrics. While plotting the flow history
   * graph an ajax call is made to this to fetch the graph data.
   * [
   * {
   *  "flowtime": 1461744881991,
   *  "runtime": 3190223,
   *  "waittime": 368011,
   *  "resourceused": 180488192,
   *  "resourcewasted": 0,
   *  "jobmetrics": [
   *          {
   *         "runtime": 3190223,
   *         "waittime": 368011,
   *         "resourceused": 180488192,
   *         "resourcewasted": 0,
   *         "jobdefurl": "sampleURL"
   *         "jobexecurl": "sampleURL"
   *          }
   *        ]
   * },
   * {
   *  "flowtime": 1461818409959,
   *  "runtime": 897490,
   *  "waittime": 100703,
   *  "resourceused": 12863488,
   *  "resourcewasted": 0,
   *  "jobmetrics": [
   *          {
   *         "runtime": 897490,
   *         "waittime": 100703,
   *         "resourceused": 12863488,
   *         "resourcewasted": 0,
   *         "jobdefurl": "sampleURL"
   *         "jobexecurl": "sampleURL"
   * }
   * ]
   *}
   *]
   **/
  public static Result restFlowMetricsGraphData(String flowDefId) {
    JsonArray datasets = new JsonArray();
    if (flowDefId == null || flowDefId.isEmpty()) {
      return ok(new Gson().toJson(datasets));
    }

    List<AppResult> results = getRestFlowAppResults(flowDefId);

    if (results.size() == 0) {
      logger.info("No results for Job url");
    }
    Map<IdUrlPair, List<AppResult>> flowExecIdToJobsMap =
        ControllerUtil.limitHistoryResults(ControllerUtil.groupJobs(results, ControllerUtil.GroupBy.FLOW_EXECUTION_ID), results.size(), MAX_HISTORY_LIMIT);

    // Compute the graph data starting from the earliest available execution to latest
    List<IdUrlPair> keyList = new ArrayList<IdUrlPair>(flowExecIdToJobsMap.keySet());
    for (int i = keyList.size() - 1; i >= 0; i--) {
      IdUrlPair flowExecPair = keyList.get(i);
      int flowPerfScore = 0;
      JsonArray jobScores = new JsonArray();
      List<AppResult> mrJobsList = Lists.reverse(flowExecIdToJobsMap.get(flowExecPair));
      Map<IdUrlPair, List<AppResult>> jobDefIdToJobsMap = ControllerUtil.groupJobs(mrJobsList, ControllerUtil.GroupBy.JOB_DEFINITION_ID);

      long totalFlowMemoryUsed = 0;
      long totalFlowMemoryWasted = 0;
      long totalFlowDelay = 0;
      long totalFlowRuntime = 0;
      // Compute the execution records. Note that each entry in the jobDefIdToJobsMap will have at least one AppResult
      for (IdUrlPair jobDefPair : jobDefIdToJobsMap.keySet()) {
        // Compute job perf score
        long totalJobMemoryUsed = 0;
        long totalJobMemoryWasted = 0;
        long totalJobDelay = 0;
        long totalJobRuntime = 0;

        totalJobRuntime = Utils.getTotalRuntime(jobDefIdToJobsMap.get(jobDefPair));
        totalJobDelay = Utils.getTotalWaittime(jobDefIdToJobsMap.get(jobDefPair));

        for (AppResult job : jobDefIdToJobsMap.get(jobDefPair)) {
          totalJobMemoryUsed += job.resourceUsed;
          totalJobMemoryWasted += job.resourceWasted;
        }

        // A job in jobscores list
        JsonObject jobScore = new JsonObject();
        jobScore.addProperty("runtime", totalJobRuntime);
        jobScore.addProperty("waittime", totalJobDelay);
        jobScore.addProperty("resourceused", totalJobMemoryUsed);
        jobScore.addProperty("resourcewasted", totalJobMemoryWasted);
        jobScore.addProperty("jobdefurl", jobDefPair.getUrl());
        jobScore.addProperty("jobexecurl", jobDefIdToJobsMap.get(jobDefPair).get(0).jobExecUrl);

        jobScores.add(jobScore);
        totalFlowMemoryUsed += totalJobMemoryUsed;
        totalFlowMemoryWasted += totalJobMemoryWasted;
      }

      totalFlowDelay = Utils.getTotalWaittime(flowExecIdToJobsMap.get(flowExecPair));
      totalFlowRuntime = Utils.getTotalRuntime(flowExecIdToJobsMap.get(flowExecPair));

      // Execution record
      JsonObject dataset = new JsonObject();
      dataset.addProperty("flowtime", mrJobsList.get(mrJobsList.size() - 1).finishTime);
      dataset.addProperty("runtime", totalFlowRuntime);
      dataset.addProperty("waittime", totalFlowDelay);
      dataset.addProperty("resourceused", totalFlowMemoryUsed);
      dataset.addProperty("resourcewasted", totalFlowMemoryWasted);
      dataset.add("jobmetrics", jobScores);

      datasets.add(dataset);
    }

    return ok(new Gson().toJson(datasets));
  }

  /**
   * Returns a list of AppResults after quering the FLOW_EXEC_ID from the database
   * @return The list of AppResults
   */
  private static List<AppResult> getRestJobAppResults(String jobDefId) {
    List<AppResult> results = AppResult.find.select(
        AppResult.getSearchFields() + "," + AppResult.TABLE.FLOW_EXEC_ID + "," + AppResult.TABLE.FLOW_EXEC_URL)
        .where()
        .eq(AppResult.TABLE.JOB_DEF_ID, jobDefId)
        .order()
        .desc(AppResult.TABLE.FINISH_TIME)
        .setMaxRows(JOB_HISTORY_LIMIT)
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .findList();

    return results;
  }

  /**
   * Returns the list of AppResults after quering the FLOW_DEF_ID from the database
   * @return The list of AppResults
   */
  private static List<AppResult> getRestFlowAppResults(String flowDefId) {
    // Fetch available flow executions with latest JOB_HISTORY_LIMIT mr jobs.
    List<AppResult> results = AppResult.find.select("*")
        .where()
        .eq(AppResult.TABLE.FLOW_DEF_ID, flowDefId)
        .order()
        .desc(AppResult.TABLE.FINISH_TIME)
        .setMaxRows(JOB_HISTORY_LIMIT)
        .findList();

    return results;
  }

  private static class AppResourceUsageData {
    String user;
    double resourceUsed;
    double resourceWasted;
  }

  /**
   * Returns the list of users with their resourceUsed and resourceWasted Data for the given time range
   * @return list of AppResourceUsageData
   **/
  private static Collection<AppResourceUsageData> getUserResourceUsage(Date start, Date end) {
    long resourceUsed = 0;
    Map<String, AppResourceUsageData> userResourceUsage = new HashMap<String, AppResourceUsageData>();
    // Fetch all the appresults for the given time range [startTime, endTime).
    List<AppResult> results = AppResult.find.select("*")
        .where()
        .ge(AppResult.TABLE.START_TIME, start.getTime())
        .lt(AppResult.TABLE.START_TIME, end.getTime()).findList();

    // aggregate the resourceUsage data at the user level
    for (AppResult result : results) {
      if (!userResourceUsage.containsKey(result.username)) {
        AppResourceUsageData data = new AppResourceUsageData();
        data.user = result.username;
        userResourceUsage.put(result.username, data);
      }
      userResourceUsage.get(result.username).resourceUsed += Utils.MBSecondsToGBHours(result.resourceUsed);
      userResourceUsage.get(result.username).resourceWasted += Utils.MBSecondsToGBHours(result.resourceWasted);
    }

    return userResourceUsage.values();
  }
}
