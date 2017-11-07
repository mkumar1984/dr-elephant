package models;

import java.sql.Timestamp;

import play.db.ebean.Model;

public class JobSavedState extends Model {
  /** */
  private static final long serialVersionUID = 1L;

  public static class TABLE {
    public static final String TABLE_NAME = "job";
    public static final String jobId = "jobId";
    public static final String savedState = "savedState";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  public Integer jobId;
  public byte[] savedState;
  public Timestamp createdTs;
  public Timestamp updatedTs;

  public static Finder<Integer, JobSavedState> find = new Finder<Integer, JobSavedState>(Integer.class, JobSavedState.class);
}
