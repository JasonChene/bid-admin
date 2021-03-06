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

import cn.teleinfo.bidadmin.soybean.bo.UserBO;
import cn.teleinfo.bidadmin.soybean.entity.Clockln;
import cn.teleinfo.bidadmin.soybean.entity.Group;
import cn.teleinfo.bidadmin.soybean.entity.User;
import cn.teleinfo.bidadmin.soybean.entity.WxSubscribe;
import cn.teleinfo.bidadmin.soybean.service.IClocklnService;
import cn.teleinfo.bidadmin.soybean.service.IGroupService;
import cn.teleinfo.bidadmin.soybean.service.IUserService;
import cn.teleinfo.bidadmin.soybean.service.IWxSubscribeService;
import cn.teleinfo.bidadmin.soybean.vo.ClocklnVO;
import cn.teleinfo.bidadmin.soybean.vo.UserVO;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.*;
import io.swagger.models.auth.In;
import lombok.AllArgsConstructor;
import org.springblade.core.boot.ctrl.BladeController;
import org.springblade.core.mp.support.Condition;
import org.springblade.core.mp.support.Query;
import org.springblade.core.tool.api.R;
import org.springblade.core.tool.utils.StringUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 *  控制器
 *
 * @author Blade
 * @since 2020-02-21
 */
@RestController
@AllArgsConstructor
@RequestMapping("/wx/clockln/census/")
@Api(value = "统计界面接口", tags = "统计界面接口")
public class WxClocklnCensusController extends BladeController {

	private IClocklnService clocklnService;

	private IGroupService groupService;

	private IUserService userService;

	private IWxSubscribeService wxSubscribeService;

	/**
	 * 获取群组分页打卡信息
	 */
	@GetMapping("/hospitalization")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "群打卡信息分页", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query"),
			@ApiImplicitParam(name = "hospitalization", value = "健康参数:1确诊，2隔离，3，出隔离，4其他", paramType = "query")
	})
	public R<IPage<ClocklnVO>> hospitalization(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime,@RequestParam("hospitalization") Integer hospitalization, Query query) {
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			IPage<ClocklnVO> pages = clocklnService.selectClocklnPageByGroup(Condition.getPage(query), ids, clocklnTime, null, null, hospitalization, null,null);
			return R.data(pages);
		}
		return R.data(null);
	}

	/**
	 * 获取群组分页打卡信息
	 */
	@GetMapping("/region")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "群打卡信息分页", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query"),
			@ApiImplicitParam(name = "region", value = "健康参数:1武汉，2湖北，3，北京，4其他", paramType = "query")
	})
		public R<IPage<ClocklnVO>> region(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime,@RequestParam("region") Integer region, Query query) {
		Group group= groupService.getById(groupId);

		if (group==null){
			return R.fail("该部门不存在,请输入正确的部门ID");
		}
		String province=group.getAddressName();
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			if (!StringUtil.isEmpty(province) && province.contains("，")){
				province=province.substring(0,province.lastIndexOf("，"));
			}
			IPage<ClocklnVO> pages = clocklnService.selectClocklnPageByGroup(Condition.getPage(query), ids, clocklnTime, null, region, null, null,province);
			return R.data(pages);
		}
		return R.data(null);
	}

	/**
	 * 获取群组分页打卡信息
	 */
	@GetMapping("/healthy")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "群打卡信息分页", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query"),
			@ApiImplicitParam(name = "healthy", value = "健康参数:1健康，2有发烧、咳嗽等症状，0其他", paramType = "query")
	})
	public R<IPage<ClocklnVO>> healthy(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime,@RequestParam("healthy") Integer healthy, Query query) {
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			IPage<ClocklnVO> pages = clocklnService.selectClocklnPageByGroup(Condition.getPage(query), ids, clocklnTime, healthy, null, null, null,null);
			return R.data(pages);
		}
		return R.data(null);
	}

	/**
	 * 获取在岗状态信息
	 */
	@GetMapping("/job")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "群打卡信息分页", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query"),
			@ApiImplicitParam(name = "jobstatus", value = "在岗状态:1在岗办公，2居家办公，3居家隔离,4监督隔离", paramType = "query")
	})
	public R<IPage<ClocklnVO>> job(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime,@RequestParam("jobstatus") Integer jobstatus, Query query) {
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			IPage<ClocklnVO> pages = clocklnService.selectClocklnPageByGroup(Condition.getPage(query), ids, clocklnTime, null, null, null, jobstatus,null);
			return R.data(pages);
		}
		return R.data(null);
	}
	/**
	 * 已打卡人数列表
	 */
	@GetMapping("/clockIn")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "已打卡人数列表", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query")
	})
	public R<IPage<ClocklnVO>> clockIn(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime,Query query) {
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			IPage<ClocklnVO> pages = clocklnService.selectClocklnPageByGroup(Condition.getPage(query), ids, clocklnTime, null, null, null, null,null);
			return R.data(pages);
		}
		return R.data(null);
	}

	/**
	 * 已打卡人数列表
	 */
	@GetMapping("/unClockIn")
	@ApiOperationSupport(order = 1)
	@ApiOperation(value = "未打卡人数列表", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query")
	})
	public R<List<UserVO>> unClockIn(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime) {
		UserBO user=groupService.selectUserByParentId(groupId);
		List<UserVO> users= user.getUsers();
		List<Clockln> list =new ArrayList<>();
		if (users.size() >0){
			List<Integer> ids=new ArrayList<>();
			users.forEach(x->{
				ids.add(x.getId());
			});
			list=clocklnService.selectClocklnByGroup(ids,clocklnTime);
		}
		Iterator<UserVO> iterator=users.iterator();
		while (iterator.hasNext()){
			UserVO u=iterator.next();
			for (Clockln c :list) {
				if (u.getId()==c.getUserId() || u.getId().equals(c.getUserId())){
					iterator.remove();
					break;
				}
			}
		}
		return R.data(users);
	}
	/**
	* 获取群组分页打卡信息
	*/
	@GetMapping("/list")
    @ApiOperationSupport(order = 1)
	@ApiOperation(value = "群打卡信息分页", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query")
	})
	public R<Map> list(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd")Date clocklnTime, Query query) {
		if (groupId == null){
			return R.fail("部门ID不能为空");
		}
		Group group= groupService.getById(groupId);
		if (group==null){
			return R.fail("该部门不存在,请输入正确的部门ID");
		}
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		if (ids.size()>0) {
			List<Clockln> list = new ArrayList<>();
			IPage<UserVO> users = groupService.selectUserPageAndCountByParentId(ids, Condition.getPage(query));
			if (ids.size() > 0) {
				list = clocklnService.selectClocklnByGroup(ids, clocklnTime);
			}
			boolean flag = false;
			for(UserVO x : users.getRecords()) {
				Clockln clockln = clocklnService.selectClocklnByUserID(x.getId(), clocklnTime);
				if (clockln != null) {
					x.setClockInId(clockln.getId());
					x.setHealthy(clockln.getHealthy());
					x.setAdmitting(clockln.getAdmitting());
					x.setComfirmed(clockln.getComfirmed());
				} else {
					x.setClockInId(0);
					x.setHealthy(0);
					x.setComfirmed(0);
					x.setAdmitting(0);
				}

				// 用户是否提醒打卡
				WxSubscribe wxSubscribe = wxSubscribeService.selectWxSubscribe(x.getWechatId(), null, clocklnTime);

				if (wxSubscribe == null) {
					x.setIsSendSubscribeMsg(0);
					if (x.getMessage().intValue() > 0 && clockln == null) {
						flag = true;
					}
				} else {
					x.setIsSendSubscribeMsg(1);
				}
			}

			Integer unClockIn=ids.size() - list.size();
			Map map = new HashMap();
			map.put("data", users);
			map.put("unClockInCount", unClockIn);
			map.put("isSendSubscribeMsg", 1);
			if (flag) {
				map.put("isSendSubscribeMsg", 0);
			}
			// 群组是否提醒打卡
//   			int isSendSubscribeMsg=0;
//   			int isSendSubscribeMsg=1;
//			if (unClockIn>0) {
//				boolean flag = true;
//				for (Integer id : ids) {
//					User user = userService.getById(id);
//					if (user.getMessage() > 0) {
//						flag = false;
//					}
//					if (flag) {
//						isSendSubscribeMsg = 1;
//						break;
//					}
//				}
//				WxSubscribe wxSubscribe = wxSubscribeService.selectWxSubscribe(null, groupId, clocklnTime);
//				if (wxSubscribe != null) {
//					Iterator<Integer> iterator=ids.iterator();
//					while (iterator.hasNext() ) {
//						Integer id=iterator.next();
//						for (Clockln clockln:list ) {
//							if (id == clockln.getUserId()){
//								iterator.remove();
//							}
//						}
//						User user = userService.getById(id);
//						if (user.getMessage() > 0) {
//							flag = false;
//						}
//					}
//					Integer count=0;
//					if (ids.size()>0) {
//						count = wxSubscribeService.selectWxUnSubscribeCount(ids, clocklnTime);
//						if (count != unClockIn) {
//							isSendSubscribeMsg = 1;
//						}
//					}
//				}
//			}
//			map.put("isSendSubscribeMsg", isSendSubscribeMsg);

			return R.data(map);
		}
		return R.fail("该群组下没有用户");
	}

	/**
	* 自定义分页 
	*/
	@GetMapping("/census")
    @ApiOperationSupport(order = 3)
	@ApiOperation(value = "获取统计页面所需统计数据", notes = "传入群ID和打卡日期")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "groupId", value = "群组ID", paramType = "query", dataType = "int"),
			@ApiImplicitParam(name = "clockInTime", value = "打卡日期", paramType = "query")
	})
	public String  census(@RequestParam(name = "groupId") Integer groupId, @RequestParam("clockInTime") @DateTimeFormat(pattern ="yyyy-MM-dd") Date clocklnTime) {
		String pattern ="\\d{4}(\\-|\\/|.)\\d{1,2}\\1\\d{1,2}";
		LocalDate today = LocalDate.now();


		if (groupId == null){
			return ("部门ID不能为空");
		}
		Group group= groupService.getById(groupId);

		if (group==null){
			return ("该部门不存在,请输入正确的部门ID");
		}
		String province=group.getAddressName();
		String city=province;
		if (province.contains("，")) {
			city = province.substring(0, province.lastIndexOf("，"));
		}
		List<Integer> ids=groupService.selectUserIdByParentId(groupId);
		List<Clockln> list =new ArrayList<>();
		if (ids.size() >0){
			list=clocklnService.selectClocklnByGroup(ids,clocklnTime);
		}


		double healthy=0.0;
		double healthyPer=0.0;
		double ferver=0.0;
		double ferverPer=0.0;
		double other=0.0;
		double otherPer=0.0;

		double beijing=0.0;
		double beijingPer=0.0;
		double hubei=0.0;
		double hubeiPer=0.0;
		double wuhan=0.0;
		double wuhanPer=0.0;
		double otherRegion=0.0;
		double otherRegionPer=0.0;

		double isolator = 0.0;
		double diagnosis = 0.0;
		double outisolator=0.0;

		double isolatorPer = 0.0;
		double diagnosisPer = 0.0;
		double outisolatorPer=0.0;
		double otherIsolatorPer=0.0;

		double onJob = 0.0;
		double onJobPer = 0.0;
		double awayJob = 0.0;
		double awayJobPer = 0.0;
		double haveNoJob = 0.0;
		double haveNoJobPer = 0.0;
		double superviseJob = 0.0;
		double superviseJobPer = 0.0;
		double rest = 0.0;
		double restPer = 0.0;

		int gobackBeijing=0;

		for (Clockln c:list ) {

			if (c.getComfirmed() !=null && c.getComfirmed()==2){
				diagnosis++;
			}
			if (c.getComfirmed() ==null || c.getComfirmed()!=2) {
				//返京时间+14天 出隔离器时间
				if ((c.getLeave() != null && c.getLeave()==2) || (c.getLeaveCity() != null && c.getLeaveCity() ==2)) {
					isolator++;
				} else {
					outisolator++;
				}
			}
			/*if (!StringUtil.isEmpty(c.getGobacktime())){
				try {
					String str=c.getGobacktime();
					if (str.contains("T")){
						str=str.substring(0,str.indexOf("T"));
					}
					LocalDate local=null;
					if(!StringUtil.isEmpty(str)  && Pattern.matches(pattern, str)){
						local = LocalDate.parse(str);
					}

//					if (local !=null && today.compareTo(local) == 0){
//						gobackBeijing++;
//					}
					if (local !=null && today.compareTo(local) >= 0){
						if (c.getComfirmed() ==null || c.getComfirmed()!=2) {
							//返京时间+14天 出隔离器时间
							LocalDate localDate = local.plusDays(14);
							if (today.compareTo(localDate) > 0) {
								outisolator++;
							} else {
								isolator++;
							}
						}
					}
				}catch (Exception e){
					e.printStackTrace();
				}

			}*/

			if (c.getHealthy() != null && c.getHealthy()==1){
				healthy++;
			}else if(c.getHealthy()!= null && c.getHealthy()==2){
				ferver++;
			}else {
				other++;
			}

			if (!StringUtil.isEmpty(c.getCity()) && c.getCity().contains(city)){
				beijing++;
				if (c.getLeave() != null && c.getLeave()==2){
					gobackBeijing++;
				}
			}else if(!StringUtil.isEmpty(c.getCity()) && c.getCity().contains("湖北")){
				if (!StringUtil.isEmpty(c.getCity()) && c.getCity().contains("武汉")){
					wuhan++;
				}else {
					hubei++;
				}
			}else {
				otherRegion++;
			}

			if(c.getJobstatus() != null) {
				if (c.getJobstatus() == 1) {
					onJob++;
				} else if (c.getJobstatus() == 2) {
					awayJob++;
				} else if (c.getJobstatus() == 3) {
					haveNoJob++;
				}else if (c.getJobstatus() ==4){
					superviseJob++;
				}else if (c.getJobstatus() ==5){
					rest++;
				}

			}
		}
		if (list.size()>0) {
			healthyPer = healthy / list.size() * 100;
			ferverPer = ferver / list.size() * 100;
			otherPer = other / list.size() * 100;
			beijingPer = beijing / list.size() * 100;
			hubeiPer = hubei / list.size() * 100;
			otherRegionPer = otherRegion / list.size() * 100;
			wuhanPer = wuhan / list.size() *100;
			diagnosisPer = diagnosis/list.size()*100;
			isolatorPer =isolator/list.size() *100;
			outisolatorPer=outisolator/list.size()*100;
			onJobPer =onJob/list.size() *100;
			awayJobPer =awayJob/list.size() *100;
			haveNoJobPer =haveNoJob/list.size() *100;
			superviseJobPer =superviseJob/list.size() *100;
			restPer =rest/list.size() *100;
		}
		StringBuffer buffer=new StringBuffer("{");
		//写入总体统计数据
		buffer.append(
			"\"totality\":{" +
				"\"total\":"+ids.size()+"," +
				"\"clockIn\":"+list.size()+"," +
				"\"unClockIn\":"+(ids.size()-list.size())+"," +
				"\"notInbeijing\":"+new Double(list.size()-beijing).intValue()+ "," +
				"\"goBackBeijing\":"+gobackBeijing+"," +
				"\"abnormalbody\":"+new Double(list.size()-healthy).intValue()+"," +
				"\"diagnosis\":"+new Double(diagnosis).intValue()+"," +
				"\"onJob\":"+new Double(onJob).intValue()+"," +
				"\"awayJob\":"+new Double(awayJob).intValue()+"," +
				"\"superviseJob\":"+new Double(superviseJob).intValue()+"," +
				"\"rest\":"+new Double(rest).intValue()+"," +
				"\"haveNoJob\":"+new Double(haveNoJob).intValue()+
			"},"
		);


		//计算并写入第一张饼图数据
		buffer.append(
			"\"healthy\":[" +
				"{\"name\":\"发烧，咳嗽\",\"value\":"+new Double(ferver).intValue()+",\"percent\":"+format(ferverPer)+"}," +
				"{\"name\":\"健康\",\"value\":"+ new Double(healthy).intValue()+",\"percent\":"+format(healthyPer)+"}," +
				"{\"name\":\"其他症状\",\"value\":"+new Double(other).intValue()+",\"percent\":"+format(otherPer)+"}" +
					"],"
		);

		String city1=city;
		if (city.contains("，")) {
			 city1 = city.substring(city.indexOf("，")+1, city.length());
		}
		//计算并写入第二张饼图数据
		buffer.append(
			"\"region\":[" +
				"{\"name\":\"武汉市\",\"value\":"+new Double(wuhan).intValue()+",\"percent\":"+format(wuhanPer)+"}," +
				"{\"name\":\"湖北其他\",\"value\":"+new Double(hubei).intValue()+",\"percent\":"+format(hubeiPer)+"}," +
				"{\"name\":\""+city1+"\",\"value\":"+new Double(beijing).intValue()+",\"percent\":"+format(+beijingPer)+"}," +
				"{\"name\":\"全国其他\",\"value\":"+new Double(otherRegion).intValue()+",\"percent\":"+format(otherRegionPer)+"}" +
					"],"
		);

		//计算并写入第三张饼图数据
		buffer.append(
			"\"hospitalization\":[" +
				"{\"name\":\"确诊隔离\",\"value\":"+new Double(diagnosis).intValue()+",\"percent\":"+format(+diagnosisPer)+"}," +
				"{\"name\":\"一般隔离\",\"value\":"+new Double(isolator).intValue()+",\"percent\":"+format(isolatorPer)+"}," +
				"{\"name\":\"非隔离期\",\"value\":"+new Double(outisolator).intValue()+",\"percent\":"+format(outisolatorPer)+"}" +
			"],"
		);

		//计算并写入第四张饼图数据
		buffer.append(
			"\"fugong\":[" +
				"{\"name\":\"在岗办公\",\"value\":"+new Double(onJob).intValue()+",\"percent\":"+format(+onJobPer)+"}," +
				"{\"name\":\"居家办公\",\"value\":"+new Double(awayJob).intValue()+",\"percent\":"+format(awayJobPer)+"}," +
				"{\"name\":\"居家隔离\",\"value\":"+new Double(haveNoJob).intValue()+",\"percent\":"+format(haveNoJobPer)+"}," +
				"{\"name\":\"监督隔离\",\"value\":"+new Double(superviseJob).intValue()+",\"percent\":"+format(superviseJobPer)+"}," +
				"{\"name\":\"居家休息\",\"value\":"+new Double(rest).intValue()+",\"percent\":"+format(restPer)+"}" +
			"]}"
		);

		String str =buffer.toString();
		JSONObject object=JSONObject.parseObject(str);
//		System.out.println(str);
//		str=str.replace("\"","");
		return object.toJSONString();
	}

	private String format(double in){
//		DecimalFormat df = new DecimalFormat("#.00");
		if (in==0){
			return "0";
		}
		return String.format("%.2f",in);
	}
	
}
