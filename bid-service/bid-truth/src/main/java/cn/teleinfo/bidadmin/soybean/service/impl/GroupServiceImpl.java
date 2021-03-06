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
package cn.teleinfo.bidadmin.soybean.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.teleinfo.bidadmin.soybean.bo.UserBO;
import cn.teleinfo.bidadmin.soybean.entity.Group;
import cn.teleinfo.bidadmin.soybean.entity.ParentGroup;
import cn.teleinfo.bidadmin.soybean.entity.User;
import cn.teleinfo.bidadmin.soybean.entity.UserGroup;
import cn.teleinfo.bidadmin.soybean.mapper.GroupMapper;
import cn.teleinfo.bidadmin.soybean.mapper.UserMapper;
import cn.teleinfo.bidadmin.soybean.service.*;
import cn.teleinfo.bidadmin.soybean.utils.ExcelUtils;
import cn.teleinfo.bidadmin.soybean.vo.GroupTreeVo;
import cn.teleinfo.bidadmin.soybean.vo.GroupVO;
import cn.teleinfo.bidadmin.soybean.vo.UserVO;
import cn.teleinfo.bidadmin.soybean.wrapper.UserWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.exceptions.ApiException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.annotations.Api;
import org.apache.commons.lang3.StringUtils;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.tool.utils.Func;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.FileSystemUtils;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.CollationElementIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 服务实现类
 *
 * @author Blade
 * @since 2020-02-21
 */
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements IGroupService {

    @Autowired
    private IParentGroupService parentGroupService;
    @Autowired
    private GroupMapper groupMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private IChildrenGroupService childrenGroupService;
    @Autowired
    private IUserGroupService userGroupService;
    @Autowired
    private IGroupLogService groupLogService;
    @Autowired
    private IUserService userService;

    //逗号分隔类型校验
    public static final String STRING_LIST = "\\d+(,\\d+)*";

    @Override
    public IPage<GroupVO> selectGroupPage(IPage<GroupVO> page, GroupVO group) {
        return page.setRecords(baseMapper.selectGroupPage(page, group));
    }

    @Override
    @Transactional
    public boolean saveOrUpdateGroup(Group group) {
        //新增群组
        if (group.getId() == null) {
            if (org.springframework.util.StringUtils.isEmpty(group.getParentId())) {
                throw new ApiException("上级部门不能为空");
            }
            saveGroup(group);
        } else {
            //更新群
            if (group.getId() == null) {
                throw new ApiException("主键Id不能为空");
            }
            if (!existGroup(group.getId())) {
                throw new ApiException("部门不存在");
            }
            //不提供人数修改功能
            if (group.getUserAccount() != null) {
                throw new ApiException("部门人数不能更新");
            }
            updateGroup(group);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean close(Integer groupId, Integer creatorId) {
        if (!isGroupCreater(groupId, creatorId)) {
            throw new ApiException("您不是机构创建人");
        }
        //判断是否是一级机构
        LambdaQueryWrapper<ParentGroup> queryWrapper = Wrappers.<ParentGroup>lambdaQuery().
                eq(ParentGroup::getGroupId, groupId);
        ParentGroup parentGroup = parentGroupService.getOne(queryWrapper);
        if (parentGroup == null) {
            throw new ApiException("机构不存在");
        }
        if (!Group.TOP_PARENT_ID.equals(parentGroup.getParentId())) {
            throw new ApiException("只允许解散一级机构");
        }
        //逻辑删除群下所有机构，及群成员
        Group topGroup = getGroupById(groupId);
        if (topGroup == null) {
            throw new ApiException("数据错误, 请联系管理员");
        }
        LambdaQueryWrapper<Group> groupLambdaQueryWrapper = Wrappers.<Group>lambdaQuery().
                eq(Group::getGroupIdentify, topGroup.getGroupIdentify());
        List<Group> groupList = list(groupLambdaQueryWrapper);
        for (Group group : groupList) {
            //逻辑删除群
            group.setStatus(Group.DELETE);
            updateById(group);
            //删除中间表
            LambdaQueryWrapper<ParentGroup> deleteWrapper = Wrappers.<ParentGroup>lambdaQuery().
                    eq(ParentGroup::getGroupId, group.getId());
            parentGroupService.remove(deleteWrapper);
            //逻辑删除群用户
            LambdaUpdateWrapper<UserGroup> updateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                    eq(UserGroup::getGroupId, group.getId()).
                    set(UserGroup::getStatus, UserGroup.DELETE);
            userGroupService.update(updateWrapper);
        }
        return true;
    }

    @Override
    public List<GroupTreeVo> selectAllGroupAndParent() {
        return groupMapper.tree();
    }

    @Override
    public Group detail(Group group) {
        Integer groupId = group.getId();
        Group detail = this.getGroupById(groupId);
        if (detail == null) {
            return null;
        }
        LambdaQueryWrapper<ParentGroup> parentGroupLambdaQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().
                eq(ParentGroup::getGroupId, groupId);
        ParentGroup parentGroup = parentGroupService.getOne(parentGroupLambdaQueryWrapper);
        if (parentGroup != null) {
            detail.setParentId(parentGroup.getParentId());
            detail.setSort(parentGroup.getSort());
        }
        return detail;
    }

    @Override
    public List<GroupTreeVo> treeChildren(Integer groupId) {
        List<GroupTreeVo> tree = selectAllGroupAndParent();
        if (groupId == null) {
            groupId = 0;
        }
        List<GroupTreeVo> maps = buildTree(tree, groupId);
        return maps;
    }

    /**
     * 递归构建树形下拉
     *
     * @param groups
     * @param parentId
     * @return
     */
    public List<GroupTreeVo> buildUserTree(List<GroupTreeVo> groups, Integer parentId, Integer userId, boolean manageFlag, boolean dataManageFlag) {
        List<GroupTreeVo> tree = new ArrayList<GroupTreeVo>();

        for (GroupTreeVo group : groups) {
            //获取群组ID
            Integer id = group.getId();
            //获取群组父ID
            Integer pId = group.getParentId();

            if (pId.equals(parentId)) {
                //如果用户是管理员，设置managerFlagTemp为true
                boolean managerFlagTemp = false;
                //如果用户是数据管理员，设置dataManageFlagTemp为true
                boolean dataManageFlagTemp = false;
                //查看用户是否为管理员
                List<Integer> managers = Func.toIntList(group.getManagers());
                Integer createUser = group.getCreateUser();
                if (createUser != null && createUser.equals(userId)) {
                    managerFlagTemp = true;
                }
                if (managers.contains(userId)) {
                    managerFlagTemp = true;
                }
                if (manageFlag) {
                    managerFlagTemp = true;
                }
                //查看用户是否为数据管理员
                List<Integer> dataManagers = Func.toIntList(group.getManagers());
                if (dataManagers.contains(userId)) {
                    dataManageFlagTemp = true;
                }
                if (dataManageFlag) {
                    dataManageFlagTemp = true;
                }
                List<GroupTreeVo> treeList = buildUserTree(groups, id, userId, managerFlagTemp, dataManageFlagTemp);
                //计算组织人数
                for (GroupTreeVo groupTreeVo : treeList) {
                    group.setUserAccount(group.getUserAccount() + groupTreeVo.getUserAccount());
                }
                group.setChildren(treeList);
                group.setPermission(managerFlagTemp);
                group.setDataPermission(dataManageFlagTemp);
                tree.add(group);
            }
        }
        return tree;
    }

    @Override
    public List<GroupTreeVo> treeUser(Integer userId) {
        if (!existUser(userId)) {
            throw new ApiException("用户不存在");
        }
        ArrayList<GroupTreeVo> treeRootList = new ArrayList<>();
        //过滤掉非管理员的群组
        LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getStatus, Group.NORMAL);
        List<Group> groupList = list(queryWrapper);
        //遍历获取用户管理的所有群
        List<Group> filterList = groupList.stream().filter(group -> {
            List<Integer> managerList = Func.toIntList(group.getManagers());
            Integer createUser = group.getCreateUser();
            List<Integer> dataManagerList = Func.toIntList(group.getDataManagers());
            if (managerList.contains(userId)) {
                return true;
            }
            if (createUser != null && createUser.equals(userId)) {
                return true;
            }
            if (dataManagerList.contains(userId)) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());
        //过滤掉子群组
        ArrayList<Integer> removeIds = new ArrayList<>();
        List<GroupTreeVo> groupAndParentListTemp = selectAllGroupAndParent();
        for (Group group : filterList) {
            for (Group groupTemp : filterList) {
                if (isChildrenGroup(groupAndParentListTemp, group.getId(), groupTemp.getId())) {
                    removeIds.add(groupTemp.getId());
                }
            }
        }
        filterList.removeIf(group -> {
            return removeIds.contains(group.getId());
        });
        //获取群及其子群信息
        for (Group group : filterList) {
            GroupTreeVo groupTreeVo = new GroupTreeVo();
            BeanUtils.copyProperties(group, groupTreeVo);
            if (groupTreeVo.getGroupType().equals(Group.TYPE_ORGANIZATION)) {
                groupTreeVo.setUserAccount(0);
            }
            //查询所有群
            List<GroupTreeVo> groupAndParentList = selectAllGroupAndParent();
            boolean isManager = Func.toIntList(group.getManagers()).contains(userId);
            Integer createUser = group.getCreateUser();
            if (createUser != null && createUser.equals(userId)) {
                isManager = true;
            }
            boolean isDataManager = Func.toIntList(group.getDataManagers()).contains(userId);
            List<GroupTreeVo> groupTreeVos = buildUserTree(groupAndParentList, group.getId(), userId, isManager, isDataManager);
            //计算当前群人数
            for (GroupTreeVo treeVo : groupTreeVos) {
                groupTreeVo.setUserAccount(groupTreeVo.getUserAccount() + treeVo.getUserAccount());
            }
            groupTreeVo.setPermission(isManager);
            groupTreeVo.setDataPermission(isDataManager);
            groupTreeVo.setChildren(groupTreeVos);
            treeRootList.add(groupTreeVo);
        }
        //添加用户加入但没管理权限的群
        LambdaQueryWrapper<UserGroup> userGroupQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                eq(UserGroup::getUserId, userId).
                eq(UserGroup::getStatus, UserGroup.NORMAL);
        List<UserGroup> userGroups = userGroupService.list(userGroupQueryWrapper);
        //获取所有群ID
        List<Integer> groupIds = userGroups.stream().map(UserGroup::getGroupId).
                distinct().collect(Collectors.toList());
        //遍历群ID查看是否有管理权限
        List<Integer> ids = groupList.stream().map(Group::getId).collect(Collectors.toList());
        for (Integer groupId : groupIds) {
            //群不存在，则跳过
            if (!ids.contains(groupId)) {
                continue;
            }
            //没有管理权限则添加进列表，并设置Permission为false
            Group checkGroup = getGroupById(groupId);
            boolean isManager = Func.toIntList(checkGroup.getManagers()).contains(userId);
            boolean isDataManager = Func.toIntList(checkGroup.getDataManagers()).contains(userId);
            Integer createUser = checkGroup.getCreateUser();
            boolean isCreater = false;
            if (createUser != null && createUser.equals(userId)) {
                isCreater = true;
            }
            if (!isManager && !isDataManager && !isCreater) {
                //校验是否为其管理群的子群组
                boolean flag = false;
                for (Group group : filterList) {
                    if (isChildrenGroup(groupAndParentListTemp, group.getId(), groupId)) {
                        flag = true;
                    }
                }
                if (flag == true) {
                    continue;
                }
                LambdaQueryWrapper<Group> groupQueryWrapper = Wrappers.<Group>lambdaQuery().
                        eq(Group::getId, groupId).
                        eq(Group::getStatus, Group.NORMAL);
                Group group = getOne(groupQueryWrapper);
                GroupTreeVo groupTreeVo = new GroupTreeVo();
                BeanUtils.copyProperties(group, groupTreeVo);
                groupTreeVo.setPermission(false);
                groupTreeVo.setDataPermission(false);
                treeRootList.add(groupTreeVo);
            }
        }
        return treeRootList;
    }

    private boolean isGroupMangerOrCreater(Integer groupId, Integer userId) {
        if (groupId == null) {
            throw new ApiException("部门ID不能为null");
        }
        if (userId == null) {
            throw new ApiException("用户ID不能为null");
        }
        Group group = this.getGroupById(groupId);
        if (group == null) {
            throw new ApiException("部门不存在");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new ApiException("用户不存在");
        }
        List<Integer> managerList = Func.toIntList(group.getManagers());
        Integer createUser = group.getCreateUser();
        if (managerList.contains(userId)) {
            return true;
        }
        if (createUser != null && createUser.equals(userId)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isGroupManger(Integer groupId, Integer userId) {
        if (groupId == null) {
            throw new ApiException("部门ID不能为null");
        }
        if (userId == null) {
            throw new ApiException("用户ID不能为null");
        }
        Group group = this.getGroupById(groupId);
        if (group == null) {
            throw new ApiException("部门不存在");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new ApiException("用户不存在");
        }
        List<Integer> managerList = Func.toIntList(group.getManagers());
        if (managerList.contains(userId)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isGroupCreater(Integer groupId, Integer userId) {
        if (groupId == null) {
            throw new ApiException("部门ID不能为null");
        }
        if (userId == null) {
            throw new ApiException("用户ID不能为null");
        }
        Group group = this.getGroupById(groupId);
        if (group == null) {
            throw new ApiException("机构不存在");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new ApiException("用户不存在");
        }
        Integer createUser = group.getCreateUser();
        if (createUser != null && createUser.equals(userId)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean existGroup(Integer groupId) {
        if (groupId == null) {
            throw new ApiException("部门ID不能为空");
        }
        Group group = getGroupById(groupId);
        if (group == null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean existUser(Integer userId) {
        if (userId == null) {
            throw new ApiException("用户ID不存在");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }
        return true;
    }

    public Group getGroupById(Integer groupId) {
        if (groupId == null) {
            throw new ApiException("部门ID不能为Null");
        }
        Group group = new Group();
        group.setId(groupId);
        group.setStatus(Group.NORMAL);
        return getOne(Condition.getQueryWrapper(group));
    }

    public List<GroupTreeVo> getAllGroupIdByParentId(List<GroupTreeVo> groups, Integer parentId, List groupList) {
        List<GroupTreeVo> tree = new ArrayList<GroupTreeVo>();
        for (GroupTreeVo group : groups) {
            //获取群组ID
            Integer id = group.getId();
            //获取群组父ID
            Integer pId = group.getParentId();

            if (pId.equals(parentId)) {
                List<GroupTreeVo> treeList = getAllGroupIdByParentId(groups, id, groupList);
                groupList.add(id);
            }
        }
        return tree;
    }

    @Override
    public IPage<UserVO> selectUserPageByParentId(Integer parentId, IPage<User> page) {
        List<Integer> userIds = selectUserIdByParentId(parentId);
        if (CollectionUtils.isEmpty(userIds)) {
            return UserWrapper.build().pageVO(page);
        }
        //获取所有用户
        LambdaQueryWrapper<User> userQueryWrapper = Wrappers.<User>lambdaQuery().in(User::getId, userIds);
        IPage<User> userIPage = userService.page(page, userQueryWrapper);
        return UserWrapper.build().pageVO(userIPage);
    }

    @Override
    public IPage<UserVO> selectUserPageAndCountByParentId(List<Integer> userIds, IPage<User> page) {
        //获取所有用户
        LambdaQueryWrapper<User> userQueryWrapper = Wrappers.<User>lambdaQuery().in(User::getId, userIds);
        IPage<User> userIPage = userService.page(page, userQueryWrapper);
        return UserWrapper.build().pageVO(userIPage);
    }

    @Override
    public UserBO selectUserByParentId(Integer parentId) {
        if (!existGroup(parentId)) {
            throw new ApiException("部门不存在");
        }

        List<GroupTreeVo> groupAndParent = selectAllGroupAndParent();
        //当前群及子群ID集合
        ArrayList<Integer> groupIds = new ArrayList<>();
        //添加父群ID
        groupIds.add(parentId);
        //获取所有子群Id
        getAllGroupIdByParentId(groupAndParent, parentId, groupIds);
        //获取所有用户ID
        LambdaQueryWrapper<UserGroup> userGroupQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                in(UserGroup::getGroupId, groupIds).
                eq(UserGroup::getStatus, UserGroup.NORMAL);
        List<UserGroup> userGroups = userGroupService.list(userGroupQueryWrapper);
        //为空时返回null
        if (CollectionUtils.isEmpty(userGroups)) {
            return new UserBO();
        }
        List<Integer> userIds = new ArrayList<>();
        userGroups.forEach(x -> {
            if (!userIds.contains(x.getUserId())) {
                userIds.add(x.getUserId());
            }
        });
        //获取所有用户
        LambdaQueryWrapper<User> userQueryWrapper = Wrappers.<User>lambdaQuery().in(User::getId, userIds);
        List<User> userIPage = userService.list(userQueryWrapper);

        List<Group> groups = groupMapper.selectBatchIds(userGroups.stream().map(UserGroup::getGroupId).collect(Collectors.toList()));

        UserBO ub = new UserBO();
        ub.setUserGroups(userGroups);
        ub.setGroups(groups);
        ub.setUsers(UserWrapper.build().listVO(userIPage));
        return ub;
    }

    @Override
    public List<Integer> selectUserIdByParentId(Integer parentId) {
        if (!existGroup(parentId)) {
            throw new ApiException("部门不存在");
        }

        List<GroupTreeVo> groupAndParent = selectAllGroupAndParent();
        //当前群及子群ID集合
        ArrayList<Integer> groupIds = new ArrayList<>();
        //添加父群ID
        groupIds.add(parentId);
        //获取所有子群Id
        getAllGroupIdByParentId(groupAndParent, parentId, groupIds);
        //获取所有用户ID
        LambdaQueryWrapper<UserGroup> userGroupQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                in(UserGroup::getGroupId, groupIds).
                eq(UserGroup::getStatus, UserGroup.NORMAL);
        List<UserGroup> userGroups = userGroupService.list(userGroupQueryWrapper);
        List<Integer> userIds = new ArrayList<>();
        userGroups.forEach(x -> {
            if (!userIds.contains(x.getUserId())) {
                userIds.add(x.getUserId());
            }
        });
        return userIds;
    }

    @Override
    @Transactional
    public boolean removeGroupByIds(String ids) {
        ArrayList<Group> groups = new ArrayList<>();
        for (Integer id : Func.toIntList(ids)) {
            Group group = new Group();
            group.setId(id);
            group.setStatus(Group.DELETE);
            groups.add(group);
            //设置用户在群中的状态为已删除
            LambdaUpdateWrapper<UserGroup> userGroupUpdateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                    eq(UserGroup::getGroupId, id).
                    set(UserGroup::getStatus, UserGroup.DELETE);
            userGroupService.update(userGroupUpdateWrapper);
        }
        updateBatchById(groups);
        return true;
    }

    @Override
    @Transactional
    public boolean saveGroup(Group group) {
        if (group.getId() != null) {
            throw new ApiException("部门主键ID只能为空");
        }
        if (group.getUserAccount() != null) {
            throw new ApiException("部门人数只能为空");
        }
        if (group.getStatus() != null) {
            throw new ApiException("部门状态只能为空");
        }
        checkParentGroupAndManager(group);
        //生成机构唯一码
        String groupCode = generateGroupCode();
        String groupIdentify = null;
        //如果是一级机构则机构标识码等于机构唯一码，否则机构标识码取上级机构的机构标识码
        Integer parentId = group.getParentId();
        if (Group.TOP_PARENT_ID.equals(parentId)) {
            groupIdentify = groupCode;
        } else {
            Group parentGroup = getGroupById(parentId);
            if (parentGroup == null) {
                throw new ApiException("上级机构不存在，请联系管理员");
            }
            groupIdentify = parentGroup.getGroupIdentify();
        }
        //设置机构唯一码和机构标识码
        group.setGroupCode(groupCode);
        group.setGroupIdentify(groupIdentify);
        //设置群人数为0
        group.setUserAccount(0);
        //新增群
        group.setStatus(Group.NORMAL);
        save(group);
        //保存中间表
        ParentGroup parentGroup = new ParentGroup();
        parentGroup.setGroupId(group.getId());
        parentGroup.setParentId(parentId);
        parentGroup.setSort(group.getSort());
        parentGroupService.save(parentGroup);
        return true;
    }

    @Override
    @Transactional
    public boolean wxSaveGroup(GroupVO groupVO) {
        Group group = new Group();
        group.setName(groupVO.getName());
        group.setParentId(groupVO.getParentId());
        group.setCreateUser(groupVO.getCreateUser());
        Integer parentId = group.getParentId();
        Integer createUser = group.getCreateUser();
        //上级机构不能是顶级机构
        if (Group.TOP_PARENT_ID.equals(parentId)) {
            throw new ApiException("上级机构不能是顶级机构");
        }
        //获取上级部门
        Group superiorGroup = getGroupById(parentId);
        if (superiorGroup == null) {
            throw new ApiException("上级机构不存在，请联系管理员");
        }
        //校验机构名称是否重复
        List<Group> juniorGroups = getJuniorGroups(parentId);
        for (Group juniorGroup : juniorGroups) {
            if (juniorGroup.getName().equals(group.getName())) {
                throw new ApiException(superiorGroup.getName() + "下不可以存在多个" + group.getName());
            }
        }
        //获取机构标识码
        String groupIdentify = superiorGroup.getGroupIdentify();
        //获取一级部门
        Group firstGroup = getFirstGroup(groupIdentify);
        //logo为空取一级机构logo
        if (StringUtils.isBlank(group.getLogo())) {
            group.setLogo(firstGroup.getLogo());
        }
        //校验是否为一级机构创建人
        if (!firstGroup.getCreateUser().equals(createUser)) {
            throw new ApiException("您不是一级机构创建人");
        }
        //如果是末级部门, 则变更部门类型，并移动人员到变动人员部门中
        Integer superiorGroupType = superiorGroup.getGroupType();
        if (Group.TYPE_PERSON.equals(superiorGroupType)) {
            //变更上级部门类型
            superiorGroup.setGroupType(Group.TYPE_ORGANIZATION);
            //人数清零
            superiorGroup.setUserAccount(0);
            updateById(superiorGroup);
            //判断变动人员部门是否存在
            LambdaQueryWrapper<Group> noDeptQueryWrapper = Wrappers.<Group>lambdaQuery().
                    eq(Group::getGroupCode, groupIdentify + "_" + Group.NO_DEPT_CODE).eq(Group::getStatus, Group.NORMAL);
            Group noDeptGroup = getOne(noDeptQueryWrapper);
            //变动人员部门不存在，创建部门
            if (noDeptGroup == null) {
                Group newNoDeptGroup = new Group();
                newNoDeptGroup.setName(Group.NO_DEPT_NAME);
                newNoDeptGroup.setUserAccount(0);
                newNoDeptGroup.setAddressName(firstGroup.getAddressName());
                newNoDeptGroup.setDetailAddress(firstGroup.getDetailAddress());
                newNoDeptGroup.setGroupCode(groupIdentify + "_" + Group.NO_DEPT_CODE);
                newNoDeptGroup.setGroupIdentify(groupIdentify);
                newNoDeptGroup.setFullName(firstGroup.getName() + "_" + newNoDeptGroup.getName());
                newNoDeptGroup.setCreateUser(createUser);
                newNoDeptGroup.setStatus(Group.NORMAL);
                newNoDeptGroup.setGroupType(Group.TYPE_PERSON);
                save(newNoDeptGroup);
                //创建中间表
                ParentGroup parentGroup = new ParentGroup();
                parentGroup.setGroupId(newNoDeptGroup.getId());
                parentGroup.setParentId(firstGroup.getId());
                parentGroupService.save(parentGroup);
                noDeptGroup = newNoDeptGroup;
            }
            //获取上级机构下所有人员
            List<Integer> userIdList = selectUserIdByParentId(parentId);
            //取消改机构下所有用户管理员和数据管理员权限
            for (Integer userId : userIdList) {
                userGroupService.deleteAllPermission(userId, groupIdentify);
            }
            //把改机构下所有人员移动到变动人员部门
            LambdaUpdateWrapper<UserGroup> userGroupUpdateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                    eq(UserGroup::getGroupId, parentId).set(UserGroup::getGroupId, noDeptGroup.getId());
            userGroupService.update(userGroupUpdateWrapper);
            //计算变动部门人数
            LambdaQueryWrapper<UserGroup> countQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                    eq(UserGroup::getGroupId, noDeptGroup.getId()).eq(UserGroup::getStatus, Group.NORMAL);
            int count = userGroupService.count(countQueryWrapper);
            //更新人数
            LambdaUpdateWrapper<Group> countUpdateWrapper = Wrappers.<Group>lambdaUpdate().
                    eq(Group::getId, noDeptGroup.getId()).set(Group::getUserAccount, count);
            update(countUpdateWrapper);
        }
        //开始创建机构
        //生成机构唯一码
        String groupCode = generateGroupCode();
        //设置机构唯一码和机构标识码
        group.setGroupCode(groupCode);
        group.setFullName(superiorGroup.getName() + "_" + group.getName());
        group.setGroupType(Group.TYPE_PERSON);
        group.setGroupIdentify(groupIdentify);
        group.setAddressName(superiorGroup.getAddressName());
        group.setDetailAddress(superiorGroup.getDetailAddress());
        //设置部门人数为0
        group.setUserAccount(0);
        //设置部门状态
        group.setStatus(Group.NORMAL);
        save(group);
        //保存中间表
        ParentGroup parentGroup = new ParentGroup();
        parentGroup.setGroupId(group.getId());
        parentGroup.setParentId(parentId);
        parentGroupService.save(parentGroup);
        return true;
    }

    @Override
    @Transactional
    public boolean wxRemoveGroup(Integer groupId, Integer userId) {
        Group group = getGroupById(groupId);
        //获取机构标识码
        String groupIdentify = group.getGroupIdentify();
        //获取一级机构
        Group firstGroup = getFirstGroup(groupIdentify);
        //校验是否为一级机构创建人
        if (!firstGroup.getCreateUser().equals(userId)) {
            throw new ApiException("您不是一级机构创建人");
        }
        //一级部门不允许删除
        if (firstGroup.getId().equals(groupId)) {
            throw new ApiException("一级机构不允许删除");
        }
        if (Group.TOP_PARENT_ID.equals(groupId)) {
            throw new ApiException("顶级机构不能删除");
        }
        //获取上级机构
        LambdaQueryWrapper<ParentGroup> parentGroupQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getGroupId, groupId);
        ParentGroup currentParentGroup = parentGroupService.getOne(parentGroupQueryWrapper);
        if (currentParentGroup == null) {
            throw new ApiException("数据异常，此部门上级部门不存在");
        }
        //变动部门不允许删除
        if (group.getGroupCode().equals(groupIdentify + "_" + Group.NO_DEPT_CODE)) {
            throw new ApiException(Group.NO_DEPT_NAME + "部门不能删除");
        }
        //判断变动人员部门是否存在
        LambdaQueryWrapper<Group> noDeptQueryWrapper = Wrappers.<Group>lambdaQuery().
                eq(Group::getGroupCode, groupIdentify + "_" + Group.NO_DEPT_CODE).eq(Group::getStatus, Group.NORMAL);
        Group noDeptGroup = getOne(noDeptQueryWrapper);
        //变动人员部门不存在，创建部门
        if (noDeptGroup == null) {
            Group newNoDeptGroup = new Group();
            newNoDeptGroup.setName(Group.NO_DEPT_NAME);
            newNoDeptGroup.setAddressName(firstGroup.getAddressName());
            newNoDeptGroup.setDetailAddress(firstGroup.getDetailAddress());
            newNoDeptGroup.setGroupCode(groupIdentify + "_" + Group.NO_DEPT_CODE);
            newNoDeptGroup.setGroupIdentify(groupIdentify);
            newNoDeptGroup.setFullName(firstGroup.getName() + "_" + newNoDeptGroup.getName());
            newNoDeptGroup.setCreateUser(userId);
            newNoDeptGroup.setStatus(Group.NORMAL);
            newNoDeptGroup.setGroupType(Group.TYPE_PERSON);
            save(newNoDeptGroup);
            //创建中间表
            ParentGroup parentGroup = new ParentGroup();
            parentGroup.setGroupId(newNoDeptGroup.getId());
            parentGroup.setParentId(firstGroup.getId());
            parentGroupService.save(parentGroup);
            noDeptGroup = newNoDeptGroup;
        }
        //获取机构下所有人员
        List<Integer> userIdList = selectUserIdByParentId(groupId);
        //取消改机构下所有用户管理员和数据管理员权限
        for (Integer id : userIdList) {
            userGroupService.deleteAllPermission(id, groupIdentify);
        }
        //获取机构下所有部门
        List<GroupTreeVo> groupAndParent = selectAllGroupAndParent();
        ArrayList<Integer> groupIds = new ArrayList<>();
        groupIds.add(groupId);
        getAllGroupIdByParentId(groupAndParent, groupId, groupIds);
        //移动人员到变动人员部门
        for (Integer id : groupIds) {
            //把改机构下所有人员移动到变动人员部门
            LambdaUpdateWrapper<UserGroup> userGroupUpdateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                    eq(UserGroup::getGroupId, id).set(UserGroup::getGroupId, noDeptGroup.getId());
            userGroupService.update(userGroupUpdateWrapper);
            //删除部门
            Group delGroup = new Group();
            delGroup.setId(id);
            delGroup.setStatus(Group.DELETE);
            updateById(delGroup);
            //删除中间表
            LambdaQueryWrapper<ParentGroup> delQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getGroupId, id);
            parentGroupService.remove(delQueryWrapper);
        }
        //如果上级部门下没有子部门，则更改类型为个人部门
        LambdaQueryWrapper<ParentGroup> parentQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getParentId, currentParentGroup.getParentId());
        if (parentGroupService.count(parentQueryWrapper) == 0) {
            Group superiorGroup = new Group();
            superiorGroup.setId(currentParentGroup.getParentId());
            superiorGroup.setGroupType(Group.TYPE_PERSON);
            updateById(superiorGroup);
        }
        //计算变动部门人数
        LambdaQueryWrapper<UserGroup> countQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                eq(UserGroup::getGroupId, noDeptGroup.getId()).eq(UserGroup::getStatus, Group.NORMAL);
        int count = userGroupService.count(countQueryWrapper);
        //更新人数
        LambdaUpdateWrapper<Group> countUpdateWrapper = Wrappers.<Group>lambdaUpdate().
                eq(Group::getId, noDeptGroup.getId()).set(Group::getUserAccount, count);
        update(countUpdateWrapper);
        //如果上级部门下只剩下变动人员部门，则删除变动部门，移动人员到一级机构
        List<ParentGroup> childGroupList = parentGroupService.list(parentQueryWrapper);
        if (childGroupList.size() == 1) {
            ParentGroup parentGroup = childGroupList.get(0);
            Group soleGroup = getGroupById(parentGroup.getGroupId());
            if (soleGroup == null) {
                throw new ApiException("数据异常，请联系管理员");
            }
            if (soleGroup.getGroupCode().endsWith(Group.NO_DEPT_CODE)) {
                //移动变动人员部门的人员到一级机构
                LambdaUpdateWrapper<UserGroup> userGroupUpdateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                        eq(UserGroup::getGroupId, noDeptGroup.getId()).set(UserGroup::getGroupId, firstGroup.getId());
                userGroupService.update(userGroupUpdateWrapper);
                //删除变动人员部门
                noDeptGroup.setStatus(Group.DELETE);
                updateById(noDeptGroup);
                //删除中间表
                LambdaQueryWrapper<ParentGroup> delQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getGroupId, noDeptGroup.getId());
                parentGroupService.remove(delQueryWrapper);
//                //更改一级机构类型
//                firstGroup.setGroupType(Group.TYPE_PERSON);
//                updateById(firstGroup);
                //计算一级机构人数
                LambdaQueryWrapper<UserGroup> firstCountQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                        eq(UserGroup::getGroupId, firstGroup.getId()).eq(UserGroup::getStatus, Group.NORMAL);
                int firstCount = userGroupService.count(firstCountQueryWrapper);
                //更新人数和机构状态并清空所有管理员和数据管理员
                LambdaUpdateWrapper<Group> firstCountUpdateWrapper = Wrappers.<Group>lambdaUpdate().eq(Group::getId, firstGroup.getId()).
                        set(Group::getUserAccount, firstCount).set(Group::getDataManagers, "").
                        set(Group::getManagers, "").set(Group::getGroupType, Group.TYPE_PERSON);
                update(firstCountUpdateWrapper);
            }
        }
        return true;
    }

    @Override
    public Group getFirstGroup(String groupIdentify) {
        LambdaQueryWrapper<Group> topQueryWrapper = Wrappers.<Group>lambdaQuery().
                eq(Group::getGroupCode, groupIdentify).eq(Group::getStatus, Group.NORMAL);
        return getOne(topQueryWrapper);
    }

    @Override
    public List<Group> getJuniorGroups(Integer parentId) {
        LambdaQueryWrapper<ParentGroup> parentGroupQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getParentId, parentId);
        List<Integer> groupIdList = parentGroupService.list(parentGroupQueryWrapper).stream().map(ParentGroup::getGroupId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(groupIdList)) {
            return new ArrayList<Group>();
        }
        LambdaQueryWrapper<Group> groupQueryWrapper = Wrappers.<Group>lambdaQuery().in(Group::getId, groupIdList).eq(Group::getStatus, Group.NORMAL);
        return list(groupQueryWrapper);
    }

    private boolean checkParentGroupAndManager(Group group) {
        //未指定群ID，默认父群组为顶级群组
        Integer groupId = group.getId();
        Integer parentId = group.getParentId();
        if (parentId == null) {
            group.setParentId(Group.TOP_PARENT_ID);
        } else {
            if (!existGroup(parentId)) {
                throw new ApiException("上级部门不存在");
            }
            Integer groupType = getGroupById(parentId).getGroupType();
            if (Group.TYPE_PERSON.equals(groupType)) {
                throw new ApiException("上级部门不能是末级部门");
            }
            if (groupId != null) {
                if (groupId.equals(parentId)) {
                    throw new ApiException("不能指定自己为上级部门");
                }
                List<GroupTreeVo> groupAndParentList = selectAllGroupAndParent();
                //校验是否为子群组
                if (isChildrenGroup(groupAndParentList, groupId, parentId)) {
                    throw new ApiException("不能指定当前的下级部门为上级部门");
                }
            }
        }
        //校验管理员是否存在
        String managers = group.getManagers();
        if (!StringUtils.isEmpty(managers) && !Pattern.matches(Group.PATTERN_STRING_LIST, managers)) {
            throw new ApiException("管理员ids格式不正确，格式为: 1,2,3");
        }
        for (Integer id : Func.toIntList(managers)) {
            if (!existUser(id)) {
                throw new ApiException("管理员不存在");
            }
        }
        //校验统计管理员是否存在
        String dataManagers = group.getDataManagers();
        if (!StringUtils.isEmpty(dataManagers) && !Pattern.matches(Group.PATTERN_STRING_LIST, dataManagers)) {
            throw new ApiException("数据管理员ids格式不正确，格式为: 1,2,3");
        }
        for (Integer id : Func.toIntList(dataManagers)) {
            if (!existUser(id)) {
                throw new ApiException("数据管理员不存在");
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean updateGroup(Group group) {
        //校验父群和子群格式是否正确
        checkParentGroupAndManager(group);
        Integer groupId = group.getId();
        Integer parentId = group.getParentId();
        //如果原来的上级机构不是顶级机构，更新时不允许设置上级机构为顶级机构
        LambdaQueryWrapper<ParentGroup> queryWrapper = Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getGroupId, groupId);
        Integer oldParentId = parentGroupService.getOne(queryWrapper).getParentId();
        if (!Group.TOP_PARENT_ID.equals(oldParentId) && Group.TOP_PARENT_ID.equals(parentId)) {
            throw new ApiException("修改机构信息时，不能设置上级机构为顶级机构");
        }
        //校验机构标识码是否一致
        Group currentParentGroup = getGroupById(parentId);
        Group currentGroup = getGroupById(group.getId());
        String groupIdentify = currentGroup.getGroupIdentify();
        if (groupIdentify != null && !Group.TOP_PARENT_ID.equals(parentId) && !groupIdentify.equals(currentParentGroup.getGroupIdentify())) {
            throw new ApiException("不同机构之间不允许切换上级机构");
        }
        //校验类型
        Integer groupType = group.getGroupType();
        if (Group.TYPE_PERSON.equals(groupType)) {
            //校验群下是否存在子群
            LambdaQueryWrapper<ParentGroup> parentGroupLambdaQueryWrapper = Wrappers.<ParentGroup>lambdaQuery().
                    eq(ParentGroup::getParentId, groupId);
            if (parentGroupService.count(parentGroupLambdaQueryWrapper) > 0) {
                throw new ApiException("改机构为其他机构的上级机构，不能设置为个人机构");
            }
        } else if (Group.TYPE_ORGANIZATION.equals(groupType)) {
            //校验改机构下是否有人员
            LambdaQueryWrapper<UserGroup> userGroupLambdaQueryWrapper = Wrappers.<UserGroup>lambdaQuery().
                    eq(UserGroup::getGroupId, group).eq(UserGroup::getStatus, UserGroup.NORMAL);
            if (userGroupService.count(userGroupLambdaQueryWrapper) > 0) {
                throw new ApiException("改机构有人员加入，不能更改类型");
            }
        } else {
            throw new ApiException("机构类型错误");
        }
        //更新群
        updateById(group);
        //删除中间表
        parentGroupService.remove(Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getGroupId, groupId));
        //新增中间表
        ParentGroup parentGroup = new ParentGroup();
        parentGroup.setGroupId(groupId);
        parentGroup.setParentId(parentId);
        parentGroup.setSort(group.getSort());
        parentGroupService.save(parentGroup);
        return true;
    }

    @Override
    public List<Group> children(Group group) {
        Integer groupId = this.getOne(Condition.getQueryWrapper(group)).getId();
        List<ParentGroup> parentGroup = parentGroupService.list(Wrappers.<ParentGroup>lambdaQuery().eq(ParentGroup::getParentId, groupId));
        if (CollectionUtils.isEmpty(parentGroup)) {
            return null;
        }
        List<Integer> groupList = parentGroup.stream().map(ParentGroup::getGroupId).collect(Collectors.toList());
        List<Group> list = this.list(Wrappers.<Group>lambdaQuery().in(Group::getId, groupList));
        return list;
    }

    @Override
    public List<Group> select() {
        Group group = new Group();
        group.setStatus(Group.NORMAL);
        List<Group> groups = this.list(Condition.getQueryWrapper(group));
        return groups;
    }

    /**
     * 递归构建树形下拉
     *
     * @param groups
     * @param parentId
     * @return
     */
    public List<GroupTreeVo> buildTree(List<GroupTreeVo> groups, Integer parentId) {
        List<GroupTreeVo> tree = new ArrayList<GroupTreeVo>();

        for (GroupTreeVo group : groups) {
            //获取群组ID
            Integer id = group.getId();
            //获取群组父ID
            Integer pId = group.getParentId();

            if (pId.equals(parentId)) {
                List<GroupTreeVo> treeList = buildTree(groups, id);
                //计算组织人数
                for (GroupTreeVo groupTreeVo : treeList) {
                    group.setUserAccount(group.getUserAccount() + groupTreeVo.getUserAccount());
                }
                group.setChildren(treeList);
                tree.add(group);
            }
        }
        return tree;
    }

    /**
     * 递归校验是否是子群
     *
     * @param groups   所有群
     * @param parentId 群ID
     * @param checkId  父群ID
     */
    public boolean isChildrenGroup(List<GroupTreeVo> groups, Integer parentId, Integer checkId) {
        boolean flag = false;
        for (GroupTreeVo group : groups) {
            //获取群组ID
            Integer id = group.getId();
            //获取群组父ID
            Integer pId = group.getParentId();
            if (pId.equals(parentId)) {
                flag = isChildrenGroup(groups, id, checkId);
                if (flag) {
                    return true;
                }
                if (id.equals(checkId)) {
//                    throw new ApiException("不能设置子群为父ID");
                    return true;
                }
            }
        }
        return false;
    }

    public List<Group> readGroupExcel(String excelFile) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ArrayList<Group> groups = new ArrayList<>();
        // 创建远程url连接对象
        try {
            URL url = new URL(excelFile);
            // 通过远程url连接对象打开一个连接，强转成httpURLConnection类
            connection = (HttpURLConnection) url.openConnection();
            // 设置连接方式：get
            connection.setRequestMethod("GET");
            // 设置连接主机服务器的超时时间：15000毫秒
            connection.setConnectTimeout(15000);
            // 设置读取远程返回的数据时间：60000毫秒
            connection.setReadTimeout(60000);
            // 发送请求
            connection.connect();
            // 通过connection连接，获取输入流
            if (connection.getResponseCode() == 200) {
                inputStream = connection.getInputStream();
            } else {
                throw new ApiException("获取文件异常");
            }
            ExcelReader reader = ExcelUtil.getReader(inputStream);
            List<List<Object>> groupList = reader.read(17);
            for (List<Object> groupRow : groupList) {
                Group group = new Group();
                group.setParentName(groupRow.get(0).toString());
                group.setName(groupRow.get(1).toString());
                String parentName = group.getParentName();
                String name = group.getName();
                if (!StringUtils.isBlank(parentName) && StringUtils.isBlank(name))
                    throw new ApiException("请补全" + parentName + "的下级机构");
                if (!StringUtils.isBlank(name) && StringUtils.isBlank(parentName))
                    throw new ApiException("请补全" + name + "的上级机构");
                group.setContact(groupRow.get(2).toString());
                group.setPhone(groupRow.get(3).toString());
                String province = groupRow.get(4).toString();
                String city = groupRow.get(5).toString();
                String district = groupRow.get(6).toString();
                Integer flag = 0;
                if (!StringUtils.isBlank(province))
                    flag++;
                if (!StringUtils.isBlank(city))
                    flag++;
                if (!StringUtils.isBlank(district))
                    flag++;
                if (flag > 0 && flag < 3)
                    throw new ApiException("请补全" + name + "机构所在省份、城市、区");
                group.setAddressName(flag.equals(3) ? province + "，" + city + "，" + district : "");
                group.setDetailAddress(groupRow.get(7).toString());
                groups.add(group);
            }
            return groups;
        } catch (IOException e) {
            throw new ApiException("文件读取失败");
        } finally {
            // 关闭资源
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new ApiException("断开与Excel资源连接异常");
                }
            }
            // 断开与远程地址url的连接
            connection.disconnect();
        }
    }

    @Override
    @Transactional
    public String excelImport(Group topGroup, String excelFile) {
        //一级机构机构唯一码，每个机构的唯一码不同，替换机构ID
        String topCode = generateGroupCode();
        //生成机构标识码，机构及其子机构标识码相同，用来标识一个整体机构
        String groupIdentify = topCode;
        //excel地址为空默认创建一个个人组织
        if (StringUtils.isBlank(excelFile)) {
            //一级组织名称不能重复
            LambdaQueryWrapper<Group> groupLambdaQueryWrapper = Wrappers.<Group>lambdaQuery().
                    eq(Group::getName, topGroup.getName()).eq(Group::getStatus, Group.NORMAL);
            if (count(groupLambdaQueryWrapper) > 0) {
                throw new ApiException("一级机构名称不能重名");
            }
            //查询一级组织父组织名称
            String topParentName = getGroupById(Group.TOP_PARENT_ID).getName();
            //创建一级组织
            topGroup.setStatus(Group.NORMAL);
            topGroup.setFullName(topParentName + "_" + topGroup.getName());
            topGroup.setGroupType(Group.TYPE_PERSON);
            topGroup.setGroupCode(topCode);
            topGroup.setGroupIdentify(groupIdentify);
            save(topGroup);
            //维护一级组织中间表
            ParentGroup topParentGroup = new ParentGroup();
            topParentGroup.setGroupId(topGroup.getId());
            topParentGroup.setParentId(Group.TOP_PARENT_ID);
            parentGroupService.save(topParentGroup);
            return topCode;
        }
        List<Group> metaGroups = readGroupExcel(excelFile);
        //过滤空数据
        List<Group> groups = metaGroups.stream().filter(group -> {
            if (!StringUtils.isBlank(group.getName()) && !StringUtils.isBlank(group.getParentName())) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());
        //模板校验
        if (CollectionUtils.isEmpty(groups)) {
            throw new ApiException("模板格式错误，或者数据为空");
        }
        for (Group group : groups) {
            //去除空格
            group.setName(StringUtils.trim(group.getName()));
            group.setParentName(StringUtils.trim(group.getParentName()));
            group.setAddressName(StringUtils.trim(group.getAddressName()));
            group.setPhone(StringUtils.trim(group.getPhone()));
            group.setContact(StringUtils.trim(group.getContact()));
            group.setDetailAddress(StringUtils.trim(group.getDetailAddress()));
            //设置全称
            group.setFullName(group.getParentName() + "_" + group.getName());
            //设置地址
            String addressName = group.getAddressName();
            if (StringUtils.isBlank(addressName)) {
                addressName = topGroup.getAddressName();
                group.setAddressName(addressName);
            }
            //校验地址格式
            if (!StringUtils.isBlank(addressName)) {
                String[] split = addressName.split("，");
                if (split.length != 3) {
                    throw new ApiException("单位地址格式错误");
                }
            }
            //表格中详细地址为空时，地址取一级机构详细地址
            String detailAddress = group.getDetailAddress();
            if (StringUtils.isBlank(detailAddress)) {
                detailAddress = topGroup.getDetailAddress();
                group.setDetailAddress(detailAddress);
            }
            //如果logo为空，则logo取一级机构logo
            String logo = group.getLogo();
            group.setLogo(StringUtils.isBlank(logo) ? topGroup.getLogo() : logo);
            //校验部门是否是末级组织
            boolean lastGroup = true;
            for (Group parentGroup : groups) {
                if (parentGroup.getParentName().equals(group.getName())) {
                    lastGroup = false;
                    break;
                }
            }
            //设置部门类型
            group.setGroupType(lastGroup ? Group.TYPE_PERSON : Group.TYPE_ORGANIZATION);
            //校验每个部门的下一级部门是否名称重复
            long count = groups.stream().filter(filterGroup -> filterGroup.getName().equals(group.getName()) && filterGroup.getParentName().equals(group.getParentName())).count();
            if (count > 1) {
                throw new ApiException(group.getParentName() + "下不可以存在多个" + group.getName());
            }
        }
        //一级组织名称不能重复
        LambdaQueryWrapper<Group> groupLambdaQueryWrapper = Wrappers.<Group>lambdaQuery().
                eq(Group::getName, topGroup.getName()).eq(Group::getStatus, Group.NORMAL);
        if (count(groupLambdaQueryWrapper) > 0) {
            throw new ApiException("一级部门名称不能重名");
        }
        //查询一级组织父组织名称
        String topParentName = getGroupById(Group.TOP_PARENT_ID).getName();
        //创建一级组织
        topGroup.setStatus(Group.NORMAL);
        topGroup.setFullName(topParentName + "_" + topGroup.getName());
        topGroup.setGroupType(Group.TYPE_ORGANIZATION);
        topGroup.setGroupCode(topCode);
        topGroup.setGroupIdentify(groupIdentify);
        save(topGroup);
        //维护一级组织中间表
        ParentGroup topParentGroup = new ParentGroup();
        topParentGroup.setGroupId(topGroup.getId());
        topParentGroup.setParentId(Group.TOP_PARENT_ID);
        parentGroupService.save(topParentGroup);
        //保存所有子群
        for (Group group : groups) {
            group.setCreateUser(topGroup.getCreateUser());
            group.setStatus(Group.NORMAL);
            //校验管理员电话是否重复
            LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().
                    eq(Group::getPhone, group.getPhone()).eq(Group::getGroupType, Group.TYPE_PERSON);
            int count = count(queryWrapper);
            if (count != 0) {
                group.setContact(null);
                group.setPhone(null);
            }
            group.setGroupCode(generateGroupCode());
            group.setGroupIdentify(groupIdentify);
            save(group);
        }
        //组装一个包含一级组织的群组
        ArrayList<Group> allGroups = new ArrayList<>();
        allGroups.add(topGroup);
        allGroups.addAll(groups);
        //维护子群组中间表
        for (Group group : groups) {
            ParentGroup parentGroup = new ParentGroup();
            parentGroup.setGroupId(group.getId());
            //查询父Id
            List<Group> groupList = allGroups.stream().filter(filterGroup -> {
                String name = filterGroup.getName();
                if (name == null) {
                    throw new ApiException("部门名称不能为空");
                } else {
                    return name.equals(group.getParentName());
                }
            }).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(groupList)) {
                throw new ApiException("未发现" + group.getName() + "的上级部门");
            }
            if (groupList.size() > 1) {
                throw new ApiException("一个部门只能有一个上级部门");
            }
            //设置父ID
            parentGroup.setParentId(groupList.get(0).getId());
            //保存中间表
            parentGroupService.save(parentGroup);
        }
        return topCode;
    }

    @Override
    public List<Group> getUserManageGroups(Integer userId) {
        //获取用户是管理员的群
        LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getStatus, Group.NORMAL);
        List<Group> managerGroups = list(queryWrapper);
        List<Group> managerList = managerGroups.stream().filter(group -> {
            String managers = group.getManagers();
            if (Func.toIntList(managers).contains(userId)) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());
        return managerList;
    }

    @Override
    public List<Group> getUserManageGroups(Integer userId, String groupIdentify) {
        //获取指定机构下用户是管理员的群
        List<Group> userManageGroups = getUserManageGroups(userId);
        return userManageGroups.stream().filter(group -> group.getGroupIdentify().equals(groupIdentify)).collect(Collectors.toList());
    }

    @Override
    public List<Group> getUserDataManageGroups(Integer userId) {
        //获取用户是管理员的群
        LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getStatus, Group.NORMAL);
        List<Group> dataManagerGroups = list(queryWrapper);
        List<Group> dataManagerList = dataManagerGroups.stream().filter(group -> {
            String dataManagers = group.getDataManagers();
            if (Func.toIntList(dataManagers).contains(userId)) {
                return true;
            }
            return false;
        }).collect(Collectors.toList());
        return dataManagerList;
    }

    @Override
    public List<Group> getUserDataManageGroups(Integer userId, String groupIdentify) {
        //获取指定机构下用户是管理员的群
        List<Group> userDataManageGroups = getUserDataManageGroups(userId);
        return userDataManageGroups.stream().filter(group -> group.getGroupIdentify().equals(groupIdentify)).collect(Collectors.toList());
    }

    public String generateGroupCode() {
        String data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String[] split = data.split("");
        String code = "";
        while (true) {
            Set<String> groupCodeSet = RandomUtil.randomEleSet(Arrays.asList(split), 4);
            code = StringUtils.join(groupCodeSet, "");
            LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getGroupCode, code);
            int count = count(queryWrapper);
            if (count == 0) {
                break;
            }
        }
        return code;
    }
}

