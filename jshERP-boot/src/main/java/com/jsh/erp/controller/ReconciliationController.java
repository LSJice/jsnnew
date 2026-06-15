package com.jsh.erp.controller;

import com.alibaba.fastjson.JSON;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.constants.BusinessConstants;
import com.jsh.erp.datasource.entities.ReconciliationHead;
import com.jsh.erp.datasource.entities.ReconciliationItem;
import com.jsh.erp.datasource.entities.DepotItem;
import com.jsh.erp.datasource.vo.DepotHeadVo4List;
import com.jsh.erp.datasource.vo.DepotHeadVo4StatementAccount;
import com.jsh.erp.service.DepotHeadService;
import com.jsh.erp.service.reconciliation.ReconciliationService;
import com.jsh.erp.datasource.mappers.DepotItemMapperEx;
import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.erp.utils.Constants;
import com.jsh.erp.utils.StringUtil;
import com.jsh.erp.utils.Tools;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping(value = "/reconciliation")
@Api(tags = {"对账单管理"})
public class ReconciliationController {
    private Logger logger = LoggerFactory.getLogger(ReconciliationController.class);

    @Resource
    private ReconciliationService reconciliationService;

    @Resource
    private DepotHeadService depotHeadService;

    @Resource
    private DepotItemMapperEx depotItemMapperEx;

    /**
     * 创建对账单
     */
    @PostMapping(value = "/create")
    @ApiOperation(value = "创建对账单")
    public BaseResponseInfo create(@RequestParam("head") String headJson,
                                   @RequestParam("items") String itemsJson,
                                   HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            ReconciliationHead head = JSON.parseObject(headJson, ReconciliationHead.class);
            List<ReconciliationItem> items = JSON.parseArray(itemsJson, ReconciliationItem.class);

            // 防重复检查：检查是否有单据已对账
            List<String> alreadyReconciled = reconciliationService.checkAlreadyReconciled(items);
            if (!alreadyReconciled.isEmpty()) {
                res.code = 500;
                res.data = "以下单据已对账，不能重复对账：" + String.join("、", alreadyReconciled);
                return res;
            }

            Long headId = reconciliationService.createBill(head, items);
            Map<String, Object> map = new HashMap<>();
            map.put("id", headId);
            res.code = 200;
            res.data = map;
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "创建对账单失败";
        }
        return res;
    }

    /**
     * 查询预览数据（汇总+明细）
     */
    @GetMapping(value = "/getPreview")
    @ApiOperation(value = "查询对账单预览数据")
    public BaseResponseInfo getPreview(@RequestParam("organType") String organType,
                                       @RequestParam("organId") Long organId,
                                       @RequestParam("beginTime") String beginTime,
                                       @RequestParam("endTime") String endTime,
                                       @RequestParam(value = "statusFilter", required = false) String statusFilter,
                                       HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            String type = "";
            String subType = "";
            String orderSubType = "";
            if (("供应商").equals(organType)) {
                type = "入库";
                subType = "采购";
                orderSubType = "采购订单";
            } else if (("客户").equals(organType)) {
                type = "出库";
                subType = "销售";
                orderSubType = "销售订单";
            }

            beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
            endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);

            // 订单状态过滤：前端值 → 关联订单的 status 值
            // 判断依据是入库/出库单据所关联的采购/销售订单（link_number）当前的交货状态
            String orderStatus = null;
            if (statusFilter != null && !statusFilter.isEmpty()) {
                switch (statusFilter) {
                    case "done":    orderStatus = "2"; break;  // 已完全交货 → 订单完成采购/销售
                    case "partial": orderStatus = "3"; break;  // 部分交货 → 订单部分采购/销售
                    case "none":    orderStatus = "1"; break;  // 未交货 → 订单已审核但未采购/销售
                    default:        orderStatus = null; break;
                }
            }

            // 获取明细列表（过滤已对账的明细）
            List<DepotItem> items = depotItemMapperEx.selectItemsForReconciliation(
                    type, subType, beginTime, endTime, organId, orderStatus, orderSubType);

            // 从明细数据计算汇总
            BigDecimal periodAmount = BigDecimal.ZERO;
            BigDecimal paidAmount = BigDecimal.ZERO;
            if (items != null) {
                for (DepotItem item : items) {
                    if (item.getAllPrice() != null) {
                        periodAmount = periodAmount.add(item.getAllPrice());
                    }
                    // paidAmount 由前端根据明细数据自行计算
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("beginAmount", BigDecimal.ZERO);
            summary.put("periodAmount", periodAmount);
            summary.put("paidAmount", paidAmount);
            summary.put("endAmount", periodAmount.subtract(paidAmount));

            Map<String, Object> data = new HashMap<>();
            data.put("summary", summary);
            data.put("items", items);
            res.code = 200;
            res.data = data;
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取预览数据失败";
        }
        return res;
    }

    /**
     * 查询对账单列表
     */
    @GetMapping(value = "/list")
    @ApiOperation(value = "查询对账单列表")
    public BaseResponseInfo list(@RequestParam(value = Constants.SEARCH, required = false) String search,
                                 @RequestParam("currentPage") Integer currentPage,
                                 @RequestParam("pageSize") Integer pageSize,
                                 HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            String organType = StringUtil.getInfo(search, "organType");
            String organIdStr = StringUtil.getInfo(search, "organId");
            Long organId = StringUtil.isEmpty(organIdStr) ? null : Long.parseLong(organIdStr);
            String billNo = StringUtil.getInfo(search, "billNo");
            String beginTime = StringUtil.getInfo(search, "beginTime");
            String endTime = StringUtil.getInfo(search, "endTime");
            String isPaidStr = StringUtil.getInfo(search, "isPaid");
            Integer isPaid = StringUtil.isEmpty(isPaidStr) ? null : Integer.parseInt(isPaidStr);
            String isInvoicedStr = StringUtil.getInfo(search, "isInvoiced");
            Integer isInvoiced = StringUtil.isEmpty(isInvoicedStr) ? null : Integer.parseInt(isInvoicedStr);
            String createMonth = StringUtil.getInfo(search, "createMonth");
            logger.info("=== 对账单列表查询参数: organType={}, organId={}, billNo={}, beginTime={}, endTime={}, isPaid={}, isInvoiced={}, createMonth={}, currentPage={}, pageSize={} ===",
                    organType, organId, billNo, beginTime, endTime, isPaid, isInvoiced, createMonth, currentPage, pageSize);
            beginTime = Tools.parseDayToTime(beginTime, BusinessConstants.DAY_FIRST_TIME);
            endTime = Tools.parseDayToTime(endTime, BusinessConstants.DAY_LAST_TIME);
            logger.info("=== 对账单列表: beginTime={}, endTime={} ===", beginTime, endTime);
            List<ReconciliationHead> list = reconciliationService.findList(organType, organId, billNo,
                    beginTime, endTime, isPaid, isInvoiced, createMonth, (currentPage - 1) * pageSize, pageSize);
            long total = reconciliationService.findCount(organType, organId, billNo,
                    beginTime, endTime, isPaid, isInvoiced, createMonth);
            logger.info("=== 对账单列表: 查询结果 list.size={}, total={} ===", list != null ? list.size() : 0, total);
            if (list != null && !list.isEmpty()) {
                logger.info("=== 对账单列表: 第一条数据 id={}, billNo={}, organName={}, totalAmount={} ===",
                        list.get(0).getId(), list.get(0).getBillNo(), list.get(0).getOrganName(), list.get(0).getTotalAmount());
                logger.info("=== 对账单列表: 第一条数据 creatorName={} ===", list.get(0).getCreatorName());
            }
            map.put("rows", list);
            map.put("total", total);
            res.code = 200;
            res.data = map;
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 查询主表详情
     */
    @GetMapping(value = "/head")
    @ApiOperation(value = "查询对账单主表详情")
    public BaseResponseInfo getHead(@RequestParam("id") Long id,
                                    HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            ReconciliationHead head = reconciliationService.getHeadById(id);
            // 查询创建人姓名（原生 selectByPrimaryKey 不会 JOIN 用户表）
            String creatorName = null;
            if (head.getCreator() != null) {
                creatorName = reconciliationService.getCreatorNameById(head.getCreator());
            }
            // 格式化日期字段为字符串，避免 FastJSON 序列化为时间戳
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", head.getId());
            data.put("billNo", head.getBillNo());
            data.put("organType", head.getOrganType());
            data.put("organId", head.getOrganId());
            data.put("organName", head.getOrganName());
            data.put("beginTime", formatDate(head.getBeginTime()));
            data.put("endTime", formatDate(head.getEndTime()));
            data.put("totalAmount", head.getTotalAmount());
            data.put("isPaid", head.getIsPaid());
            data.put("payTime", formatDate(head.getPayTime()));
            data.put("isInvoiced", head.getIsInvoiced());
            data.put("invoiceCode", head.getInvoiceCode());
            data.put("invoiceTime", formatDate(head.getInvoiceTime()));
            data.put("creator", head.getCreator());
            data.put("creatorName", creatorName);
            data.put("createTime", formatDate(head.getCreateTime()));
            data.put("remark", head.getRemark());
            data.put("templateId", head.getTemplateId());
            logger.info("=== 对账单详情 head={} ===", JSON.toJSONString(data));
            res.code = 200;
            res.data = data;
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    private String formatDate(Date date) {
        if (date == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    /**
     * 查询从表列表
     */
    @GetMapping(value = "/item")
    @ApiOperation(value = "查询对账单从表列表")
    public BaseResponseInfo getItems(@RequestParam("headId") Long headId,
                                     HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<ReconciliationItem> items = reconciliationService.getItemsByHeadId(headId);
            Map<String, Object> map = new HashMap<>();
            map.put("rows", items);
            res.code = 200;
            res.data = map;
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }

    /**
     * 更新付款/开票状态
     */
    @PutMapping(value = "/updateStatus")
    @ApiOperation(value = "更新付款/开票状态")
    public BaseResponseInfo updateStatus(@RequestParam("id") Long id,
                                         @RequestParam(value = "isPaid", required = false) Integer isPaid,
                                         @RequestParam(value = "payTime", required = false) String payTime,
                                         @RequestParam(value = "isInvoiced", required = false) Integer isInvoiced,
                                         @RequestParam(value = "invoiceCode", required = false) String invoiceCode,
                                         @RequestParam(value = "invoiceTime", required = false) String invoiceTime,
                                         HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date payTimeDate = null;
            Date invoiceTimeDate = null;
            if (payTime != null && payTime.trim().length() > 0) {
                payTimeDate = sdf.parse(payTime.trim().length() == 10 ? payTime.trim() + " 00:00:00" : payTime.trim());
            }
            if (invoiceTime != null && invoiceTime.trim().length() > 0) {
                invoiceTimeDate = sdf.parse(invoiceTime.trim().length() == 10 ? invoiceTime.trim() + " 00:00:00" : invoiceTime.trim());
            }
            reconciliationService.updateStatus(id, isPaid, payTimeDate, isInvoiced, invoiceCode, invoiceTimeDate);
            res.code = 200;
            res.data = "操作成功";
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "更新失败";
        }
        return res;
    }

    /**
     * 编辑对账单：移除单行明细
     */
    @DeleteMapping(value = "/removeItem")
    @ApiOperation(value = "移除对账单明细行")
    public BaseResponseInfo removeItem(@RequestParam("itemId") Long itemId,
                                      HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            reconciliationService.removeItem(itemId);
            res.code = 200;
            res.data = "移除成功";
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = e.getMessage();
        }
        return res;
    }

    /**
     * 逻辑删除对账单
     */
    @DeleteMapping(value = "/delete")
    @ApiOperation(value = "删除对账单")
    public BaseResponseInfo delete(@RequestParam("id") Long id,
                                   HttpServletRequest request) throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            reconciliationService.deleteBill(id);
            res.code = 200;
            res.data = "删除成功";
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "删除失败";
        }
        return res;
    }
}
