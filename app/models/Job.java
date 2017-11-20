package models;

import java.sql.Timestamp;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import play.db.ebean.Model;


@Entity
@Table(name = "job_definition")
public class Job extends Model {

  private static final long serialVersionUID = 1L;
  public static final int USERNAME_LIMIT = 50;
  public static final int JOB_NAME_LIMIT = 1000;

  public static class TABLE {
    public static final String TABLE_NAME = "job";
    public static final String jobId = "jobId";
    public static final String projectName = "projectName";
    public static final String hostName = "hostName";
    public static final String flowDefId = "flowDefId";
    public static final String jobDefId = "jobDefId";
    public static final String flowDefUrl = "flowDefUrl";
    public static final String jobDefUrl = "jobDefUrl";
    public static final String algoId = "algoId";
    public static final String scheduler = "scheduler";
    public static final String username = "username";
    public static final String client = "client";
    public static final String tuningEnabled = "tuningEnabled";
    public static final String averageResourceUsage = "averageResourceUsage";
    public static final String averageExecutionTime = "averageExecutionTime";
    public static final String allowedMaxResourceUsagePercent = "allowedMaxResourceUsagePercent";
    public static final String allowedMaxExecutionTimePercent = "allowedMaxExecutionTimePercent";
    public static final String deleted = "deleted";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer jobId;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String projectName;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String hostName;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String flowDefId;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String jobDefId;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String flowDefUrl;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String jobDefUrl;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String client;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name="algo", joinColumns={@JoinColumn(name ="algo_id", referencedColumnName="algo_id")})
  public Algo algo;

  @Column(length = USERNAME_LIMIT, nullable = false)
  public String scheduler;

  @Column(length = USERNAME_LIMIT, nullable = false)
  public String username;

  @Column(nullable = false)
  public Boolean tuningEnabled;

  @Column(nullable = true)
  public Double averageResourceUsage;

  @Column(nullable = true)
  public Double averageExecutionTime;

  @Column(nullable = true)
  public Double allowedMaxResourceUsagePercent;

  @Column(nullable = true)
  public Double allowedMaxExecutionTimePercent;

  @Column(nullable = true)
  public Boolean deleted;

  @Column(nullable = true)
  public Timestamp createdTs;

  @Column(nullable = true)
  public Timestamp updatedTs;

  public static Finder<Integer, Job> find = new Finder<Integer, Job>(Integer.class, Job.class);

}
