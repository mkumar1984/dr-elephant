package models;

import java.sql.Timestamp;

import play.db.ebean.Model;

public class JobSuggestedParamValue extends Model{

  private static final long serialVersionUID = 1L;
  public static class TABLE {
    public static final String TABLE_NAME = "job_execution";
    public static final String paramSetId = "paramSetId";
    public static final String paramId = "paramId";
    public static final String paramValue = "paramValue";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  public Long paramSetId;
  public Integer paramId;
  public String paramValue;
  public Timestamp createdTs;
  public Timestamp updatedTs;

}
