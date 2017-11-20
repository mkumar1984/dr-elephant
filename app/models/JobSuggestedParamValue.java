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

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.*;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import play.db.ebean.Model;


@Entity
@Table(name = "job_suggested_param_value")
@IdClass(JobSuggestedParamValue.PrimaryKey.class)
public class JobSuggestedParamValue extends Model {

  private static final long serialVersionUID = 1L;

  public static class TABLE {
    public static final String TABLE_NAME = "job_suggested_param_value";
    public static final String paramSetId = "primaryKeyParamSetId";
    public static final String paramId = "primaryKeyParamId";
    public static final String paramValue = "paramValue";
    public static final String createdTs = "createdTs";
    public static final String updatedTs = "updatedTs";
  }

  @EmbeddedId
  @Transient
  public PrimaryKey paramValuePK;
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

  @Embeddable
  public static class PrimaryKey implements Serializable {
    @Transient
    public Integer primaryKeyParamId;

    @Transient
    public Long primaryKeyParamSetId;

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37).append(primaryKeyParamId).append(primaryKeyParamSetId).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof PrimaryKey) {
        if (((PrimaryKey) obj).primaryKeyParamId == primaryKeyParamId
            && ((PrimaryKey) obj).primaryKeyParamSetId == primaryKeyParamSetId) {
          return true;
        }
      }
      return false;
    }
  }

  public static Finder<PrimaryKey, JobSuggestedParamValue> find = new Finder<PrimaryKey, JobSuggestedParamValue>(
      PrimaryKey.class, JobSuggestedParamValue.class);

}
