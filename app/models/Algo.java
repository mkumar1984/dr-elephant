package models;

import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import play.db.ebean.Model;

@Entity
@Table(name = "algo")
public class Algo extends Model{

  private static final long serialVersionUID = 1L;

  public enum JobType {
    PIG,
    HIVE,
    SPARK
  }

  public enum OptimizationAlgo {
    PSO
  }

  public enum OptimizationMetric {
    RESOURCE, EXECUTION_TIME, TEST_X2
  }


  public static class TABLE {
    public static final String TABLE_NAME = "algo";
    public static final String algoId = "algoId";
    public static final String jobType = "jobType";
    public static final String optimizationAlgo = "optimizationAlgo";
    public static final String optimizationAlgoVersion = "optimizationAlgoVersion";
    public static final String optimizationMetric = "optimizationMetric";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer algoId;


  @Enumerated(EnumType.STRING)
  public JobType jobType;

  @Enumerated(EnumType.STRING)
  public OptimizationAlgo optimizationAlgo;

  public Integer optimizationAlgoVersion;

  @Enumerated(EnumType.STRING)
  public OptimizationMetric OptimizationMetric;

  public Timestamp createdTs;
  public Timestamp updatedTs;

}
