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

//Todo: Rename class to job_definition

@Entity
@Table(name = "job_definition")
public class Job extends Model {

  private static final long serialVersionUID = 1L;
  public static final int USERNAME_LIMIT = 50;
  public static final int JOB_NAME_LIMIT = 1000;

  public static class TABLE {
    public static final String TABLE_NAME = "job_definition";
    public static final String id = "id";
    public static final String jobDefId = "jobDefId";
    public static final String scheduler = "scheduler";
    public static final String username = "username";
    public static final String jobName = "jobName";
    public static final String jobDefUrl = "jobDefUrl";
    public static final String flowDefinitionId = "flowDefinitionId";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer id;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String jobDefId;

  @Column(length = USERNAME_LIMIT, nullable = false)
  public String scheduler;

  @Column(length = USERNAME_LIMIT, nullable = false)
  public String username;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String jobName;

  @Column(length = JOB_NAME_LIMIT, nullable = false)
  public String jobDefUrl;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name="flow_definition", joinColumns={@JoinColumn(name ="flow_definition_id", referencedColumnName="id")})
  public FlowDefinition flowDefinition;

  @Column(nullable = true)
  public Timestamp createdTs;

  @Column(nullable = true)
  public Timestamp updatedTs;

  public static Finder<Integer, Job> find = new Finder<Integer, Job>(Integer.class, Job.class);

}
