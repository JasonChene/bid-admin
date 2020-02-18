/**
 * Copyright (c) 2018-2028, Chill Zhuang 庄骞 (smallchill@163.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.teleinfo.bidadmin.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springblade.core.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 实体类
 *
 * @author Blade
 * @since 2019-10-08
 */
@Data
@TableName("cms_stepons")
@ApiModel(value = "踩对象", description = "踩对象")
public class Stepons implements Serializable {

    private static final long serialVersionUID = 1L;

  /** 任务ID */
  /**
   * 主键id
   */
  @TableId(value = "id", type = IdType.AUTO)
  @ApiModelProperty(value = "主键id")
  private Integer id;
    /**
     * 栏目ID
     */
    @ApiModelProperty(value = "栏目ID")
    @TableField("CATEGORY_ID")
  private Integer categoryId;
    /**
     * 文章编号
     */
    @ApiModelProperty(value = "文章编号")
    @TableField("CONTENT_ID")
  private Integer contentId;

  /**
   * 踩的人
   */
  @ApiModelProperty(value = "踩的bid")
  @TableField("BID")
  private String bid;
    /**
     * 踩的人
     */
    @ApiModelProperty(value = "踩的人")
    @TableField("NAME")
  private String name;
    /**
     * 踩的人IP
     */
    @ApiModelProperty(value = "踩的人IP")
    @TableField("IP")
  private String ip;
    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    @TableField("createTime")
  private LocalDateTime createTime;


}
