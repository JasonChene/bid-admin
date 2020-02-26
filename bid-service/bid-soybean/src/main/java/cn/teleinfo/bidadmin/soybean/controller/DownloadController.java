package cn.teleinfo.bidadmin.soybean.controller;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.RowUtil;
import cn.hutool.poi.excel.WorkbookUtil;
import cn.hutool.poi.excel.cell.CellUtil;
import cn.teleinfo.bidadmin.soybean.entity.Group;
import cn.teleinfo.bidadmin.soybean.entity.Quarantine;
import cn.teleinfo.bidadmin.soybean.entity.User;
import cn.teleinfo.bidadmin.soybean.service.IClocklnService;
import cn.teleinfo.bidadmin.soybean.service.IGroupService;
import cn.teleinfo.bidadmin.soybean.service.IQuarantineService;
import cn.teleinfo.bidadmin.soybean.service.IUserGroupService;
import cn.teleinfo.bidadmin.soybean.vo.ClocklnVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiOperationSupport;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springblade.core.boot.ctrl.BladeController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

// @RestController
@Controller
@AllArgsConstructor
@RequestMapping("/download")
@Api(value = "文件下载", tags = "文件下载")
public class DownloadController extends BladeController {

    private IGroupService groupService;

    private IUserGroupService userGroupService;

    private IClocklnService clocklnService;

    private IQuarantineService quarantineService;


    /**
     * 附件
     */
    @GetMapping(value = "/annex.xlsx", produces = "application/msexcel")
    @ApiOperationSupport(order = 1)
    @ApiOperation(value = "统计数据", notes = "统计数据")
    public void annex(@ApiParam(value = "群组表soybean_group的id") @RequestParam(name = "groupid") Integer groupid,
                      @RequestParam(required = false, name = "from") Date from,
                      @RequestParam(required = false, name = "to") Date to,
                      HttpServletResponse response) {
        Workbook workbook = WorkbookUtil.createBook("model/annex.xlsx");
        try {
            /**
             * 生成Excel
             *  1. 查取群组下所有人员
             *  2. 根据所查数据分组
             */
            Group group = groupService.getById(groupid);
            if (group == null) {
                return;
            }
            from = from == null ? DateUtil.beginOfDay(DateUtil.date()) : DateUtil.beginOfDay(from);
            to = to == null ? DateUtil.endOfDay(DateUtil.date()) : DateUtil.beginOfDay(to);
            // 组内用户
            List<User> users = userGroupService.findUserByGroupId(groupid);

            // 查出打卡记录
            List<ClocklnVO> clocklns = clocklnService.findByUserIdInAndCreatetimeBetween(users.stream().map(User::getId).collect(Collectors.toList()), from, to);
            List<Quarantine> quarantines = quarantineService.findByUserIdInAndCreatetimeBetween(users.stream().map(User::getId).collect(Collectors.toList()), from, to);

            // 写入Excel数据
            List<DateTime> rangeDate = DateUtil.rangeToList(from, to, DateField.DAY_OF_YEAR);
            for (DateTime dateItem : rangeDate) {
                Sheet sheet = WorkbookUtil.getOrCreateSheet(workbook, DateUtil.formatDate(dateItem));
                Cell cell = CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 2), 0);
                cell.setCellValue(group.getName());

                // 写入用户数据
                for (int i = 0; i < users.size(); i++) {
                    User userItem = users.get(i);
                    Date fromTime = from;
                    // 打卡表
                    ClocklnVO clockln = clocklns.stream().filter(item -> {
                        boolean flag = userItem.getId().equals(item.getUserId());
                        boolean flag1 = DateUtil.isSameDay(fromTime, Date.from(item.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
                        return flag && flag1;
                    }).findFirst().orElse(null);

                    // 隔离表
                    Quarantine quarantine = quarantines.stream().filter(item -> {
                        boolean flag = userItem.getId().equals(item.getUserId());
                        boolean flag1 = DateUtil.isSameDay(fromTime, Date.from(item.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
                        return flag && flag1;
                    }).findFirst().orElse(null);


                    CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5), 0).setCellValue(userItem.getName());
                    if (clockln != null) {
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 3).setCellValue("否");
                    } else {
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 1).setCellValue(Date.from(clockln.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 2).setCellValue(group.getFullName());
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 3).setCellValue("是");
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 4).setCellValue(clockln.getLeavetime());
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 5).setCellValue(userItem.getPhone());
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 6).setCellValue(userItem.getHomeAddress() + userItem.getDetailAddress());
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 7).setCellValue(clockln.getNobackreason());
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 8).setCellValue(clockln.getGobacktime());
                    }

                    if (quarantine != null) {
                        // TODO 待梳理表字段
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 9).setCellValue(quarantine.getOtherCity() == 1 ? "是" : "否"); // 是否从其他城市返回
                        // CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5+ i), 11).setCellValue(); // 返程的交通工具中是否出现确诊的新型肺炎患者
                        // CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5+ i), 12).setCellValue(); // 返程统计.返程出发地
                        // CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5+ i), 13).setCellValue(); // 返程统计.返程日期
                        // CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5+ i), 14).setCellValue(); // 返程统计.交通方式
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 15).setCellValue(clockln.getFlight()); // 返程统计.航班/车次/车牌号
                        CellUtil.getOrCreateCell(RowUtil.getOrCreateRow(sheet, 5 + i), 16).setCellValue(clockln.getFlight()); // 返程统计.航班/车次/车牌号
                    }

                }

            }

            response.setHeader("Content-Disposition", "attachment; filename=" + "annex.xlsx");
            WorkbookUtil.writeBook(workbook, response.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
