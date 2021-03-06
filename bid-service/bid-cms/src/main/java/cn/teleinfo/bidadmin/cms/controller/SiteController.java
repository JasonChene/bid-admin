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
package cn.teleinfo.bidadmin.cms.controller;

import cn.teleinfo.bidadmin.cms.entity.Comment;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.*;
import lombok.AllArgsConstructor;
import javax.validation.Valid;

import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.Func;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.teleinfo.bidadmin.cms.entity.Site;
import cn.teleinfo.bidadmin.cms.vo.SiteVO;
import cn.teleinfo.bidadmin.cms.wrapper.SiteWrapper;
import cn.teleinfo.bidadmin.cms.service.ISiteService;
import org.springblade.core.boot.ctrl.BladeController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;

/**
 *  控制器
 *
 * @author Blade
 * @since 2019-10-08
 */
@RestController
@AllArgsConstructor
@RequestMapping("/site")
@Api(value = "", tags = "站点接口")
public class SiteController extends BladeController {

	private ISiteService siteService;

	/**
	* 详情
	*/
	@GetMapping("/detail")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "详情", notes = "传入site")
	public R<SiteVO> detail(Site site) {
		Site detail = siteService.getOne(Condition.getQueryWrapper(site));
		return R.data(SiteWrapper.build().entityVO(detail));
	}

	/**
	* 分页 
	*/
	@GetMapping("/list")
    @ApiOperationSupport(order = 2)
	@ApiOperation(value = "分页", notes = "传入site")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "name", value = "站点名称", paramType = "query", dataType = "string"),
			@ApiImplicitParam(name = "title", value = "站点标题", paramType = "query", dataType = "string"),
			@ApiImplicitParam(name = "domain", value = "域名", paramType = "query", dataType = "string")
	})
	public R<IPage<SiteVO>> list(@ApiIgnore @RequestParam Map<String, Object> site, Query query) {
		QueryWrapper<Site> queryWrapper = Condition.getQueryWrapper(site,Site.class);
		IPage<Site> pages = siteService.page(Condition.getPage(query), queryWrapper);
		return R.data(SiteWrapper.build().pageVO(pages));
	}

	/**
	* 自定义分页 
	*/
	@GetMapping("/page")
    @ApiOperationSupport(order = 3)
	@ApiOperation(value = "分页", notes = "传入site")
	public R<IPage<SiteVO>> page(SiteVO site, Query query) {
		IPage<SiteVO> pages = siteService.selectSitePage(Condition.getPage(query), site);
		return R.data(pages);
	}

	/**
	* 新增 
	*/
	@PostMapping("/save")
    @ApiOperationSupport(order = 4)
	@ApiOperation(value = "新增", notes = "传入site")
	public R save(@Valid @RequestBody Site site) {
		return R.status(siteService.save(site));
	}

	/**
	* 修改 
	*/
	@PostMapping("/update")
    @ApiOperationSupport(order = 5)
	@ApiOperation(value = "修改", notes = "传入site")
	public R update(@Valid @RequestBody Site site) {
		return R.status(siteService.updateById(site));
	}

	/**
	* 新增或修改 
	*/
	@PostMapping("/submit")
    @ApiOperationSupport(order = 6)
	@ApiOperation(value = "新增或修改", notes = "传入site")
	public R submit(@Valid @RequestBody Site site) {
		return R.status(siteService.saveOrUpdate(site));
	}

	
	/**
	* 删除 
	*/
	@PostMapping("/remove")
    @ApiOperationSupport(order = 7)
	@ApiOperation(value = "逻辑删除", notes = "传入ids")
	public R remove(@ApiParam(value = "主键集合", required = true) @RequestParam String ids) {
		return R.status(siteService.deleteLogic(Func.toIntList(ids)));
	}

	/**
	 * 获取字典
	 *
	 * @return
	 */
	@GetMapping("/findList")
	@ApiOperationSupport(order = 8)
	@ApiOperation(value = "获取站点", notes = "获取站点")
	public R<List<Site>> dictionary() {
		List<Site> tree = siteService.getList();
		return R.data(tree);
	}
	
}
