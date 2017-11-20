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

package models;

import java.sql.Timestamp;
import javax.persistence.*;
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
    public static final String paramId = "primaryKeyParamId";
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

  public static Finder<Integer, AlgoParam> find = new Finder<Integer, AlgoParam>(Integer.class, AlgoParam.class);

}
