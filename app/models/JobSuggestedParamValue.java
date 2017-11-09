package models;

import java.sql.Timestamp;

import javax.persistence.*;

import play.db.ebean.Model;


@Entity
@Table(name = "job_suggested_param_value")
public class JobSuggestedParamValue extends Model{

  private static final long serialVersionUID = 1L;
  public static class TABLE {
    public static final String TABLE_NAME = "job_suggested_param_value";
    public static final String paramSetId = "paramSetId";
    public static final String paramId = "paramId";
    public static final String paramValue = "paramValue";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  public String paramValue;
  public Timestamp createdTs;
  public Timestamp updatedTs;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name="job_execution", joinColumns={@JoinColumn(name ="param_set_id", referencedColumnName="param_set_id")})
  public JobExecution jobExecution;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name="algo_param", joinColumns={@JoinColumn(name ="param_id", referencedColumnName="param_id")})
  public AlgoParam algoParam;

  public static Finder<Integer, JobSuggestedParamValue> find = new Finder<Integer, JobSuggestedParamValue>(Integer.class, JobSuggestedParamValue.class);
}
