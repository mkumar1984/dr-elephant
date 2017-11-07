package models;

import java.sql.Timestamp;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import play.db.ebean.Model;

@Entity
@Table(name="algo_param")
public class AlgoParam extends Model{

  private static final long serialVersionUID = 1L;

  public enum ParamValueType {
    INT,
    FLOAT,
    DOUBLE,
    BOOLEAN
  }

  public static class TABLE {
    public static final String TABLE_NAME = "algo_param";
    public static final String paramId = "paramId";
    public static final String paramName = "paramName";
    public static final String algoId = "algoId";
    public static final String paramValueType = "paramValueType";
    public static final String defaultValue = "defaultValue";
    public static final String minValue = "minValue";
    public static final String maxValue = "maxValue";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer paramId;
  public String paramName;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinTable(name="algo", joinColumns={@JoinColumn(name ="algo_id", referencedColumnName="algo_id")})
  public Algo algo;

  @Enumerated(EnumType.STRING)
  public ParamValueType paramValueType;

  @Column(nullable = false)
  public String defaultValue;

  @Column(nullable = false)
  public String minValue;

  @Column(nullable = false)
  public String maxValue;

  @Column(nullable = false)
  public String stepSize;

  public Timestamp createdTs;
  public Timestamp updatedTs;
}
