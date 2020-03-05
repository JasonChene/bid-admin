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
package cn.teleinfo.bidadmin.soybean.mapper;

import cn.teleinfo.bidadmin.soybean.entity.Group;
import cn.teleinfo.bidadmin.soybean.vo.GroupTreeVo;
import cn.teleinfo.bidadmin.soybean.vo.GroupVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

/**
 *  Mapper 接口
 *
 * @author Blade
 * @since 2020-02-21
 */
public interface GroupMapper extends BaseMapper<Group> {

	/**
	 * 自定义分页
	 *
	 * @param page
	 * @param group
	 * @return
	 */
	List<GroupVO> selectGroupPage(IPage page, GroupVO group);

	/**
	 * 群组下拉树
	 * @return
	 */
	@Select("select s.id,s.name,p.parent_id as parentId,s.logo, s.managers, s.create_user createUser,s.user_account, s.group_type as userAccount from soybean_group s, soybean_parent_group p where s.status = 0 and s.id = p.group_id order by p.parent_id")
	List<GroupTreeVo> tree();
}
