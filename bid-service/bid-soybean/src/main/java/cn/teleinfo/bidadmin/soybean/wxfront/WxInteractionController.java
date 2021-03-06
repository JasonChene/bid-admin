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
package cn.teleinfo.bidadmin.soybean.wxfront;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.teleinfo.bidadmin.soybean.entity.Clockln;
import cn.teleinfo.bidadmin.soybean.entity.User;
import cn.teleinfo.bidadmin.soybean.service.IClocklnService;
import cn.teleinfo.bidadmin.soybean.service.IUserService;
import cn.teleinfo.bidadmin.soybean.utils.RegexUtil;
import cn.teleinfo.bidadmin.soybean.vo.ClocklnVO;
import cn.teleinfo.bidadmin.soybean.vo.UserClocklnVO;
import cn.teleinfo.bidadmin.soybean.wrapper.ClocklnWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiOperationSupport;
import lombok.AllArgsConstructor;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.BeanUtil;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 *  控制器
 *
 * @author Blade
 * @since 2020-02-21
 */
@RestController
@AllArgsConstructor
@RequestMapping("/wx/interaction")
@Api(value = "用户打卡交互接口", tags = "用户打卡交互接口")
public class WxInteractionController extends BladeController {

	private IClocklnService clocklnService;
	private IUserService userService;

	/**￿
	* 查看当日用户是否已打卡接口
	*/
	@GetMapping("/clock/today")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "查看当日用户是否已打卡接口", notes = "传入用户ID")
	public R<Boolean> clock(Integer userId) {
		QueryWrapper<Clockln> clocklnQueryWrapper = new QueryWrapper<>();
		clocklnQueryWrapper.eq("user_id", userId);
		LocalDateTime now = LocalDateTime.now();
		clocklnQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
		clocklnQueryWrapper.orderByDesc("id");
		clocklnQueryWrapper.last("limit 1");
		Clockln one = clocklnService.getOne(clocklnQueryWrapper);
		if (one == null) {
			return R.data(false);
		} else {
			return R.data(true);
		}
	}

	/**￿
	* 查看当天打卡信息
	*/
	@GetMapping("/show/today")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "查看指定用户当天打卡信息", notes = "传入用户ID，打卡时间，格式：2020-02-02")
	public R<IPage<ClocklnVO>> clock(Integer userId, @RequestParam(required = false, defaultValue = "") String time, Query query) {
		if (!RegexUtil.dateFormat(time)) {
			return R.fail("查询的打卡时间格式不正确，请写成2020-02-02");
		}

		QueryWrapper<Clockln> clocklnQueryWrapper = new QueryWrapper<>();
		clocklnQueryWrapper.eq("user_id", userId);

		if ("".equals(time)) {
			LocalDateTime now = LocalDateTime.now();
			clocklnQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
		} else {
			String[] times = time.split("-");
			int years = Integer.parseInt(times[0]);
			int month = Integer.parseInt(times[1]);
			int day = Integer.parseInt(times[2]);
			LocalDateTime start = LocalDateTime.of(years, month, day, 0, 0, 0);
			LocalDateTime end = LocalDateTime.of(years, month, ++day, 0, 0, 0);
			clocklnQueryWrapper.between("create_time", start, end);
		}
		IPage<Clockln> pages = clocklnService.page(Condition.getPage(query), clocklnQueryWrapper);
		IPage<ClocklnVO> clocklnVOIPage = ClocklnWrapper.build().pageVO(pages);
		List<ClocklnVO> records = clocklnVOIPage.getRecords();
		for (ClocklnVO c : records) {
			User user = userService.getById(c.getUserId());
			if(user != null) {
				c.setPhone(user.getPhone());
				c.setUserName(user.getName());
				c.setAvatarUrl(user.getAvatarUrl());
				c.setCompanyAddress(user.getCompanyAddress());
				c.setCompanyDetailAddress(user.getCompanyDetailAddress());
			}
		}
		return R.data(clocklnVOIPage);
	}

	/**￿
	* 查看当天打卡信息
	*/
	@GetMapping("/show/user")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "查看指定用户的信息及当天打卡信息", notes = "传入用户ID")
	public R<UserClocklnVO> user(Integer userId) {
		QueryWrapper<Clockln> clocklnQueryWrapper = new QueryWrapper<>();
		clocklnQueryWrapper.eq("user_id", userId);
		LocalDateTime now = LocalDateTime.now();
		clocklnQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
		clocklnQueryWrapper.orderByDesc("create_time");
		clocklnQueryWrapper.last("limit 1");

		Clockln clockln = clocklnService.getOne(clocklnQueryWrapper);
		User user = userService.getById(userId);

		UserClocklnVO uc = new UserClocklnVO();

		BeanUtil.copy(user, uc);
		BeanUtil.copy(clockln, uc);

		if (uc.getLeave() != null && uc.getLeave() == 2) {
			uc.setLeaveString("是");
		} else if(uc.getLeave() != null && uc.getLeave() == 1) {
			uc.setLeaveString("否");
		}

		if (uc.getHubei() != null && uc.getHubei() == 1) {
			uc.setHubeiString("是");
		} else if(uc.getHubei() != null && uc.getHubei() == 0) {
			uc.setHubeiString("否");
		}

		if (uc.getWuhan() != null && uc.getWuhan() == 1) {
			uc.setWuhanString("是");
		} else if(uc.getWuhan() != null && uc.getWuhan() == 0) {
			uc.setWuhanString("否");
		}

		if (uc.getHealthy() != null && uc.getHealthy() == 1) {
			uc.setHealthyString("健康");
		} else if (uc.getHealthy() != null && uc.getHealthy() == 2) {
			uc.setHealthyString("有发烧、咳嗽等症状");
		} else if (uc.getHealthy() != null && uc.getHealthy() == 0) {
			uc.setHealthyString("其他症状");
		}

		return R.data(uc);
	}

	/**￿
	* 查看当天打卡信息
	*/
	@GetMapping("/all/today")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "查看所有用户当天打卡信息", notes = "")
	public R<IPage<ClocklnVO>> clock(Query query) {
		QueryWrapper<Clockln> clocklnQueryWrapper = new QueryWrapper<>();
		LocalDateTime now = LocalDateTime.now();
		clocklnQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
		IPage<Clockln> pages = clocklnService.page(Condition.getPage(query), clocklnQueryWrapper);
		IPage<ClocklnVO> clocklnVOIPage = ClocklnWrapper.build().pageVO(pages);
		List<ClocklnVO> records = clocklnVOIPage.getRecords();
		for (ClocklnVO c : records) {
			User user = userService.getById(c.getUserId());
			if(user != null) {
				c.setPhone(user.getPhone());
				c.setUserName(user.getName());
				c.setAvatarUrl(user.getAvatarUrl());
				c.setCompanyAddress(user.getCompanyAddress());
				c.setCompanyDetailAddress(user.getCompanyDetailAddress());
			}
		}
		return R.data(clocklnVOIPage);
	}

	/**
	* 分页 
	*/
	@GetMapping("/list")
    @ApiOperationSupport(order = 2)
	@ApiOperation(value = "分页", notes = "传入用户ID")
	public R<IPage<ClocklnVO>> list(Integer userId, Query query) {
		Clockln clockln = new Clockln();
		clockln.setUserId(userId);
		query.setDescs("create_time");
		IPage<Clockln> pages = clocklnService.page(Condition.getPage(query), Condition.getQueryWrapper(clockln));
		IPage<ClocklnVO> vo = ClocklnWrapper.build().pageVO(pages);
		for (ClocklnVO c : vo.getRecords()) {
			User user = userService.getById(c.getUserId());
			if(user != null) {
				c.setPhone(user.getPhone());
				c.setUserName(user.getName());
				c.setAvatarUrl(user.getAvatarUrl());
				c.setCompanyAddress(user.getCompanyAddress());
				c.setCompanyDetailAddress(user.getCompanyDetailAddress());
			}
		}
		return R.data(vo);
	}

	/**
	* 打卡
	*/
	@PostMapping("/clock")
    @ApiOperationSupport(order = 3)
	@ApiOperation(value = "打卡", notes = "传入clockln，不要create_time字段和id字段和quarantine字段")
	public R clock(
//			       Integer userId, String address, Integer healthy, Integer hospital, Integer wuhan, String gobacktime,
//				   String remarks, Integer quarantine, String reason, Integer otherCity, String startTime,
//				   Double temperature, Integer fever, Integer fatigue, Integer hoose, Integer dyspnea, Integer diarrhea,
//				   Integer muscle, String other, String quarantionRemarks, Integer nobackreason, Integer comfirmed,
//				   Integer admitting, Integer leave, String leavetime, String backFlight
				   //, ArrayList<QuarantineTripVO> quarantineTripVOs

				   @RequestBody Clockln clockln
	) {
		if (clockln.getUserId() == null || "".equals(clockln.getUserId()) || clockln.getUserId() < 0) {
			return R.fail("用户不存在，请输入正确的用户~");
		}
		User user = userService.getById(clockln.getUserId());
		if (user == null) {
			return R.fail("用户不存在，请输入正确的用户~");
		}

		if (!RegexUtil.dateFormat(clockln.getGobacktime())) {
			return R.fail("回京时间格式不正确，请写成2020-02-02");
		}

		if (!RegexUtil.dateFormat(clockln.getLeavetime())) {
			return R.fail("离京时间格式不正确，请写成2020-02-02");
		}

		if (clockln.getId() != null) {
			clockln.setId(null);
		}

		LocalDateTime now = LocalDateTime.now();

		QueryWrapper<Clockln> clocklnQueryWrapper = new QueryWrapper<>();
		clocklnQueryWrapper.eq("user_id", clockln.getUserId());
		clocklnQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
		List<Clockln> clist = clocklnService.list(clocklnQueryWrapper);
		if (clist != null && !clist.isEmpty()) {
			return R.fail("今日已打卡~~正常打卡");
		}

		clockln.setCreateTime(now);

		if (StrUtil.contains(clockln.getAddress(), "湖北")) {
			clockln.setHubei(1);
		} else {
			QueryWrapper<Clockln> clocklnLastQueryWrapper = new QueryWrapper<>();
			clocklnLastQueryWrapper.eq("user_id", clockln.getUserId());
			clocklnLastQueryWrapper.between("create_time", DateUtil.offsetDay(DateUtil.beginOfDay(DateUtil.date()), -2), DateUtil.endOfDay(DateUtil.date()));
			List<Clockln> lastlist = clocklnService.list(clocklnLastQueryWrapper);
			boolean hubei = lastlist.stream().anyMatch(item -> StrUtil.contains(item.getHubei()+"", "1"));
			clockln.setHubei(hubei ? 1 : 0);
		}

		if (clockln.getAddress().contains("北京")) {
			clockln.setBeijing(1);

			// 是否在隔离期
			QueryWrapper<Clockln> clockln14QueryWrapper = new QueryWrapper<>();
			clockln14QueryWrapper.eq("user_id", clockln.getUserId());
			clockln14QueryWrapper.between("create_time", LocalDateTime.of(now.plusDays(-14).toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MIN));
			int count = clocklnService.count(clockln14QueryWrapper);
			if(count < 14) {
				clockln.setQuarantine(1);
			} else {
				clockln.setQuarantine(0);
			}

		} else {
			clockln.setBeijing(0);
		}

		return R.status(clocklnService.saveOrUpdate(clockln));

//		QueryWrapper<Quarantine> quarantineQueryWrapper = new QueryWrapper<>();
//		quarantineQueryWrapper.eq("user_id", userId);
//		quarantineQueryWrapper.between("create_time", LocalDateTime.of(now.toLocalDate(), LocalTime.MIN), LocalDateTime.of(now.toLocalDate(), LocalTime.MAX));
//		List<Quarantine> qlist = quarantineService.list(quarantineQueryWrapper);
//		if (qlist != null && !qlist.isEmpty()) {
//			return R.fail("今日已打卡~~隔离打卡");
//		}

//		Clockln c = new Clockln();
//		c.setUserId(userId);
//		c.setAddress(address);
//		c.setHealthy(healthy);
//		c.setHospital(hospital);
//		c.setWuhan(wuhan);
//		c.setGobacktime(gobacktime);
//		c.setRemarks(remarks);
//		c.setQuarantine(quarantine);
//		c.setReason(reason);
//		c.setCreateTime(now);
//		c.setTemperature(temperature);
//		c.setNobackreason(nobackreason);
//		c.setComfirmed(comfirmed);
//		c.setAdmitting(admitting);
//		c.setLeave(leave);
//		c.setLeavetime(leavetime);
//		c.setFlight(backFlight);
//		R.status(clocklnService.saveOrUpdate(c));

//		Quarantine q = new Quarantine();
//		q.setOtherCity(otherCity);
//		LocalDate st = LocalDate.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
//		q.setStartTime(st);
//		q.setTemperature(temperature);
//		q.setFever(fever);
//		q.setFatigue(fatigue);
//		q.setHoose(hoose);
//		q.setDyspnea(dyspnea);
//		q.setDiarrhea(diarrhea);
//		q.setMuscle(muscle);
//		q.setOther(other);
//		q.setRemarks(quarantionRemarks);
//		q.setCreateTime(now);
//		q.setAddress(address);
//		q.setUserId(userId);
//		return R.status(quarantineService.saveOrUpdate(q));

//		List<QuarantineTrip> quarantineTrips = new ArrayList<>();
//		if (quarantineTripVOs != null && !quarantineTripVOs.isEmpty()) {
//
//			for (QuarantineTripVO qv : quarantineTripVOs) {
//				QuarantineTrip qq = new QuarantineTrip();
//				qq.setCreateTime(now);
//				qq.setFlight(qv.getFlight());
//				qq.setGobackAddress(qv.getGobackAddress());
//				qq.setGobackTime(qv.getGobackTime());
//				qq.setQuarantineId(qv.getQuarantineId());
//				qq.setRemarks(qv.getRemarks());
//				qq.setTransport(qv.getTransport());
//				qq.setUserId(qv.getUserId());
//				quarantineTrips.add(qq);
//			}
//		}
//
//		return R.status(quarantineTripService.saveOrUpdateBatch(quarantineTrips));
	}
}
