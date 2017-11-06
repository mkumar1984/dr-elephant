package models;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import play.db.ebean.Model;


@Entity
@Table(name = "job_execution")
public class JobExecution extends Model {

  private static final long serialVersionUID = 1L;

  public enum ParamSetStatus {
    CREATED,
    SENT,
    EXECUTED,
    FITNESS_COMPUTED,
    DISCARDED
  }
  public enum ExecutionState {
    NOT_STARTED,
    IN_PROGRESS,
    SUCCEDED,
    FAILED
  }

  public static class TABLE {
    public static final String TABLE_NAME = "job_execution";
    public static final String paramSetId = "paramSetId";
    public static final String jobId = "jobId";
    public static final String algoId = "algoId";
    public static final String paramSetState = "paramSetState";
    public static final String isDefaultExecution = "isDefaultExecution";
    public static final String jobExecutionId = "jobExecutionId";
    public static final String flowExecutionId = "flowExecutionId";
    public static final String executionState = "executionState";
    public static final String resourceUsage = "resourceUsage";
    public static final String executionTime = "executionTime";
    public static final String inputSizeInMb = "inputSizeInMb";
    public static final String costMetric = "costMetric";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";

  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long param_set_id;

  @Column(nullable = false)
  public Integer jobId;

  @Column(nullable = false)
  public Integer algoId;

  @Column(nullable = false)
  public ParamSetStatus paramSetState;

  @Column(nullable = false)
  public Boolean isDefaultExecution;

  public String jobExecutionId;
  public String flowExecutionId;
  public ExecutionState executionState;
  public Double resourceUsage;
  public Double executionTime;
  public Long inputSizeInMb;
  public Double costMetric;
  public Timestamp createdTs;
  public Timestamp updatedTs;

}
