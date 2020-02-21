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
package cn.teleinfo.bidadmin.soybean.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springblade.core.mp.base.BaseEntity;

/**
 * 实体类
 *
 * @author Blade
 * @since 2020-02-21
 */
@Data
@TableName("soybean_group")
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "Group对象", description = "Group对象")
public class Group extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @ApiModelProperty(value = "主键")
    @TableId(value = "id", type = IdType.AUTO)
  private Integer id;
    /**
     * 群组名
     */
    @ApiModelProperty(value = "群组名")
    private String name;
    /**
     * 群组名全称
     */
    @ApiModelProperty(value = "群组名全称")
    private String fullName;
    /**
     * 备注
     */
    @ApiModelProperty(value = "备注")
    private String remarks;
    /**
     * 群logo
     */
    @ApiModelProperty(value = "群logo")
    private String logo;
    /**
     * 群人数
     */
    @ApiModelProperty(value = "群人数")
    private Integer userAccount;
    /**
     * 群管理员
     */
    @ApiModelProperty(value = "群管理员")
    private String managers;
    /**
     * 是否需要审批(0:否，1:是)
     */
    @ApiModelProperty(value = "是否需要审批(0:否，1:是)")
    private Integer approval;
    /**
     * 群组类型（公司，社区，其他）
     */
    @ApiModelProperty(value = "群组类型（公司，社区，其他）")
    private Integer groupType;
    /**
     * 公司地址ID（只有公司和社区需要）
     */
    @ApiModelProperty(value = "公司地址ID（只有公司和社区需要）")
    private Integer addressId;
    /**
     * 公司地址名称
     */
    @ApiModelProperty(value = "公司地址名称")
    private String addressName;
    /**
     * 详细地址
     */
    @ApiModelProperty(value = "详细地址")
    private String detailAddress;


}
