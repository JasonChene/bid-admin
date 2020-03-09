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

import cn.hutool.core.util.RandomUtil;
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
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
    public boolean close(Integer groupId, Integer creatorId) {
        if (!isGroupCreater(groupId, creatorId)) {
            throw new ApiException("不是部门创建人");
        }
        //逻辑删除群
        Group group = new Group();
        group.setId(groupId);
        group.setStatus(Group.DELETE);
        updateById(group);
        //逻辑删除群用户
        LambdaUpdateWrapper<UserGroup> updateWrapper = Wrappers.<UserGroup>lambdaUpdate().
                eq(UserGroup::getGroupId, groupId).
                set(UserGroup::getStatus, UserGroup.DELETE);
        userGroupService.update(updateWrapper);
        // TODO: 2020/2/29 下次升级逻辑删除
//        //逻辑删除父子
//        LambdaUpdateWrapper<ParentGroup> parentGroupLambdaUpdateWrapper = Wrappers.<ParentGroup>lambdaUpdate().
//                eq(ParentGroup::getParentId, 1).
//                set(ParentGroup::getStatus, ParentGroup.DELETE);
//        parentGroupService.update(parentGroupLambdaUpdateWrapper);
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
     * @param groups
     * @param parentId
     * @return
     */
    public List<GroupTreeVo> buildUserTree(List<GroupTreeVo> groups, Integer parentId, Integer userId, boolean manageFlag,boolean dataManageFlag) {
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
            groupTreeVo.setUserAccount(0);
            //查询所有群
            List<GroupTreeVo> groupAndParentList = selectAllGroupAndParent();
            boolean isManager = Func.toIntList(group.getManagers()).contains(userId);
            Integer createUser = group.getCreateUser();
            if (createUser != null && createUser.equals(userId)) {
                isManager = true;
            }
            boolean isDataManager = Func.toIntList(group.getDataManagers()).contains(userId);
            List<GroupTreeVo> groupTreeVos = buildUserTree(groupAndParentList, group.getId(), userId, isManager,isDataManager);
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
            boolean isDataManager = Func.toIntList(checkGroup.getDataManagers()).contains(groupId);
            Integer createUser = checkGroup.getCreateUser();
            boolean isCreater = false;
            if (createUser != null && createUser.equals(userId)) {
                isCreater = true;
            }
            if (!isManager && !isDataManager && !isCreater ) {
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
                BeanUtils.copyProperties(group,groupTreeVo);
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
            throw new ApiException("部门组不存在");
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
            return UserWrapper.build().pageVO(page);
        }
        List<Integer> userIds = new ArrayList<>();
        userGroups.forEach(x->{
            if (!userIds.contains(x.getUserId())){
                userIds.add(x.getUserId());
            }
        });
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
        userGroups.forEach(x->{
            if (!userIds.contains(x.getUserId())){
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
        //设置群人数为0
        group.setUserAccount(0);
        //新增群
        group.setStatus(Group.NORMAL);
        save(group);
        //保存中间表
        ParentGroup parentGroup = new ParentGroup();
        parentGroup.setGroupId(group.getId());
        parentGroup.setParentId(group.getParentId());
        parentGroup.setSort(group.getSort());
        parentGroupService.save(parentGroup);
        return true;
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
        //校验管理员是否存在
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
        //更新群
        updateById(group);
        Integer groupId = group.getId();
        Integer parentId = group.getParentId();
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
     * @param groups 所有群
     * @param parentId 群ID
     * @param checkId 父群ID
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

    @Override
    @Transactional
    public String excelImport(Group topGroup, String excelFile) {
        String fullName = "";
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        String topCode = generateGroupCode();
        try {
            // 创建远程url连接对象
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
            List<Group> metaGroups = ExcelUtils.importExcel(inputStream, 0,1, false, Group.class);
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
                if (StringUtils.isBlank(group.getName())) {
                    throw new ApiException("部门名称不能为空");
                }
                if (StringUtils.isBlank(group.getParentName())) {
                    throw new ApiException("上级部门名称不能为空");
                }
                //去除空格
                group.setName(group.getName().trim());
                group.setParentName(group.getParentName().trim());
                if (!StringUtils.isBlank(group.getAddressName())) {
                    group.setAddressName(group.getAddressName().trim());
                }
                if (!StringUtils.isBlank(group.getPhone())) {
                    group.setPhone(group.getPhone().trim());
                }
                if (!StringUtils.isBlank(group.getContact())) {
                    group.setContact(group.getContact().trim());
                }
                if (!StringUtils.isBlank(group.getDetailAddress())) {
                    group.setDetailAddress(group.getDetailAddress().trim());
                }
                //设置全称
                group.setFullName(group.getParentName() + "_" +group.getName());
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
                //校验部门是否是末级组织
                boolean lastGroup = true;
                for (Group parentGroup : groups) {
                    if (parentGroup.getParentName().equals(group.getName())) {
                        lastGroup = false;
                        break;
                    }
                }
                //设置部门类型
                if (lastGroup) {
                    group.setGroupType(Group.TYPE_PERSON);
                } else {
                    group.setGroupType(Group.TYPE_ORGANIZATION);
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
                fullName = group.getFullName();
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
                    throw new ApiException("未发现"+group.getName()+"的上级部门");
                }
                if (groupList.size() > 1) {
                    throw new ApiException("一个部门只能有一个上级部门");
                }
                //设置父ID
                parentGroup.setParentId(groupList.get(0).getId());
                //保存中间表
                parentGroupService.save(parentGroup);
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException("部门全称" + fullName + "已存在");
        } catch (ProtocolException e) {
            throw new ApiException("读取文件异常");
        } catch (MalformedURLException e) {
            throw new ApiException("文件读取失败");
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
        return topCode;
    }

    @Override
    public List<Group> getUserManageGroups(Integer userId) {
        //获取用户是管理员的群
        LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getStatus, Group.NORMAL);
        List<Group> managerGroups = list(queryWrapper);
        managerGroups.removeIf(group -> {
            String managers = group.getManagers();
            if (Func.toIntList(managers).contains(userId)) {
                return false;
            }
            Integer createUser = group.getCreateUser();
            if (createUser != null && createUser.equals(userId)) {
                return false;
            }
            return true;
        });
        return managerGroups;
    }

    @Override
    public List<Group> getUserDataManageGroups(Integer userId) {
        //获取用户是管理员的群
        LambdaQueryWrapper<Group> queryWrapper = Wrappers.<Group>lambdaQuery().eq(Group::getStatus, Group.NORMAL);
        List<Group> dataManagerGroups = list(queryWrapper);
        dataManagerGroups.removeIf(group -> {
            String dataManagers = group.getDataManagers();
            if (Func.toIntList(dataManagers).contains(userId)) {
                return false;
            }
            return true;
        });
        return dataManagerGroups;
    }

    /**
     * 临时解决Integer为null时, 返回前台为-1的问题
     */
    public static void modifyObject(Object model) {
        // TODO: 2020/2/27
        //获取实体类的所有属性，返回Field数组
        Field[] fields = model.getClass().getDeclaredFields();
        for (Field field : fields) {
            //获取属性的类型
            field.setAccessible(true);
            String type = field.getGenericType().toString();
            if (type.equals("class java.lang.Integer")) {
                try {
                    Object obj = field.get(model);
                    if (obj != null && Integer.valueOf(obj.toString()).equals(-1)) {
                        field.set(model, null);
                    }
                } catch (IllegalAccessException e) {
                    throw new ApiException("-1转null失败");
                }
            }
        }
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

