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
package cn.teleinfo.bidadmin.soybean.controller;

import cn.teleinfo.bidadmin.soybean.entity.User;
import cn.teleinfo.bidadmin.soybean.service.IUserService;
import cn.teleinfo.bidadmin.soybean.wrapper.UserWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiOperationSupport;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import org.bifj.crypto.Keys;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.Func;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * 控制器
 *
 * @author Blade
 * @since 2020-02-21
 */
@RestController
@AllArgsConstructor
@RequestMapping("/user")
@Api(value = "", tags = "用户")
public class UserController extends BladeController {

    private IUserService userService;

    /**
     * 是否存在
     */
    @GetMapping("/exist")
    @ApiOperationSupport(order = 1)
    @ApiOperation(value = "是否存在", notes = "是否存在user")
    public R<User> detail(@RequestParam(name = "openid") String openid) {
        User detail = userService.findByWechatId(openid);
        if (detail == null) {
            return R.data(null);
        }
        return R.data(UserWrapper.build().entityVO(detail));
    }

    /**
     * 详情
     */
    @GetMapping("/detail")
    @ApiOperationSupport(order = 1)
    @ApiOperation(value = "详情", notes = "传入user")
    public R<User> detail(User user) {
        User detail = userService.getOne(Condition.getQueryWrapper(user));
        return R.data(detail);
    }

    /**
     * 分页
     */
    @GetMapping("/select")
    @ApiOperationSupport(order = 2)
    @ApiOperation(value = "根据名字查询列表，模糊匹配", notes = "根据名字查询列表，模糊匹配，传入user")
    public R<List<User>> select(String name) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", name);
        List<User> list = userService.list(queryWrapper);
        return R.data(list);
    }

    /**
     * 分页
     */
    @GetMapping("/list")
    @ApiOperationSupport(order = 2)
    @ApiOperation(value = "分页", notes = "传入user")
    public R<IPage<User>> list(User user, Query query) {
        IPage<User> pages = userService.page(Condition.getPage(query), Condition.getQueryWrapper(user));
        return R.data(pages);
    }

    /**
     * 自定义分页
     */
    @GetMapping("/page")
    @ApiOperationSupport(order = 3)
    @ApiOperation(value = "分页", notes = "传入user")
    public R<IPage<User>> page(User user, Query query) {
        IPage<User> pages = userService.selectUserPage(Condition.getPage(query), user);
        return R.data(pages);
    }

    /**
     * 新增
     */
    @PostMapping("/save")
    @ApiOperationSupport(order = 4)
    @ApiOperation(value = "新增", notes = "传入user")
    public R save(@Valid @RequestBody User user) {
        String uuid = UUID.randomUUID().toString();
        String bid = Keys.createBID(uuid);
        user.setBidAddress(bid);
        return R.status(userService.save(user));
    }

    /**
     * 修改
     */
    @PostMapping("/update")
    @ApiOperationSupport(order = 5)
    @ApiOperation(value = "修改", notes = "传入user")
    public R update(@Valid @RequestBody User user) {
        return R.status(userService.updateById(user));
    }

    /**
     * 新增或修改
     */
    @PostMapping("/submit")
    @ApiOperationSupport(order = 6)
    @ApiOperation(value = "新增或修改", notes = "传入user")
    public R submit(@Valid @RequestBody User user) {
        String uuid = UUID.randomUUID().toString();
        String bid = Keys.createBID(uuid);
        user.setBidAddress(bid);
        return R.status(userService.saveOrUpdate(user));
    }

    /**
     * 新增或修改
     */
    @PostMapping("/saveOrUpdate")
    @ApiOperationSupport(order = 6)
    @ApiOperation(value = "新增或修改", notes = "传入user")
    public R saveOrUpdate(@Valid @RequestBody User user) {
        String uuid = UUID.randomUUID().toString();
        String bid = Keys.createBID(uuid);
        user.setBidAddress(bid);
        return R.status(userService.submit(user));
    }


    /**
     * 删除
     */
    @PostMapping("/remove")
    @ApiOperationSupport(order = 7)
    @ApiOperation(value = "逻辑删除", notes = "传入ids")
    public R remove(@ApiParam(value = "主键集合", required = true) @RequestParam String ids) {
        return R.status(userService.removeByIds(Func.toIntList(ids)));
    }


}
