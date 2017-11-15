package models;

import java.sql.Timestamp;

import javax.persistence.CascadeType;
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
@Table(name = "job_suggested_param_value")
public class JobSuggestedParamValue extends Model {

  private static final long serialVersionUID = 1L;

  public static class TABLE {
    public static final String TABLE_NAME = "job_suggested_param_value";
    public static final String paramSetId = "param_set_id";
    public static final String paramId = "param_id";
    public static final String paramValue = "paramValue";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  public String paramValue;
  public Timestamp createdTs;
  public Timestamp updatedTs;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name = "job_execution", joinColumns = { @JoinColumn(name = "param_set_id",
      referencedColumnName = "param_set_id") })
  public JobExecution jobExecution;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name = "algo_param", joinColumns = { @JoinColumn(name = "param_id", referencedColumnName = "param_id") })
  public AlgoParam algoParam;


  public static Finder<Long, JobSuggestedParamValue> find = new Finder<Long, JobSuggestedParamValue>(
      Long.class, JobSuggestedParamValue.class);

}
