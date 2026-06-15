package com.jsh.erp.service.reconciliation;

import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.mappers.*;
import com.jsh.erp.datasource.mappers.DepotItemMapper;
import com.jsh.erp.datasource.entities.DepotItemExample;
import com.jsh.erp.service.UserService;
import com.jsh.erp.service.DepotHeadService;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReconciliationService {
    private Logger logger = LoggerFactory.getLogger(ReconciliationService.class);

    @Resource
    private ReconciliationHeadMapper reconciliationHeadMapper;
    @Resource
    private ReconciliationHeadMapperEx reconciliationHeadMapperEx;
    @Resource
    private ReconciliationItemMapper reconciliationItemMapper;
    @Resource
    private DepotHeadMapper depotHeadMapper;
    @Resource
    private DepotHeadService depotHeadService;
    @Resource
    private UserService userService;
    @Resource
    private DepotItemMapper depotItemMapper;

    /**
     * 检查明细是否已对账
     * @param items 对账明细列表
     * @return 已对账的明细列表
     */
    public List<String> checkAlreadyReconciled(List<ReconciliationItem> items) throws Exception {
        List<String> alreadyReconciled = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return alreadyReconciled;
        }
        for (ReconciliationItem item : items) {
            Long detailId = item.getBillDetailId();
            if (detailId == null) continue;

            DepotItem depotItem = depotItemMapper.selectByPrimaryKey(detailId);
            if (depotItem != null && "1".equals(depotItem.getReconciliationStatus())) {
                DepotHead depotHead = depotHeadMapper.selectByPrimaryKey(depotItem.getHeaderId());
                if (depotHead != null) {
                    alreadyReconciled.add(depotHead.getNumber() + "-明细" + detailId);
                }
            }
        }
        return alreadyReconciled;
    }

    /**
     * 生成对账单号：DZ + yyyyMMdd + 4位序号
     */
    public String generateBillNo() throws Exception {
        String prefix = "DZ" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        ReconciliationHeadExample example = new ReconciliationHeadExample();
        example.createCriteria()
            .andBillNoLike(prefix + "%")
            .andDeleteFlagNotEqualTo("1");
        example.setOrderByClause("bill_no desc");
        List<ReconciliationHead> list = reconciliationHeadMapper.selectByExample(example);
        if (list != null && !list.isEmpty()) {
            String lastNo = list.get(0).getBillNo();
            int seq = Integer.parseInt(lastNo.substring(prefix.length())) + 1;
            return prefix + String.format("%04d", seq);
        }
        return prefix + "0001";
    }

    /**
     * 创建对账单（事务保存主表+从表+回写对账状态）
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public Long createBill(ReconciliationHead head, List<ReconciliationItem> items) throws Exception {
        // 1. 生成单号
        head.setBillNo(generateBillNo());
        User user = userService.getCurrentUser();
        head.setCreator(user.getId());
        head.setCreateTime(new Date());
        head.setTenantId(user.getTenantId());
        head.setDeleteFlag("0");
        // 2. 计算合计金额
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (ReconciliationItem item : items) {
                total = total.add(item.getMaterialAmount());
            }
        }
        head.setTotalAmount(total);
        // 3. 保存主表
        reconciliationHeadMapper.insert(head);
        // 4. 保存从表 + 回写明细对账状态
        Set<Long> headIdSet = new HashSet<>();
        if (items != null) {
            for (ReconciliationItem item : items) {
                item.setHeaderId(head.getId());
                item.setTenantId(user.getTenantId());
                item.setDeleteFlag("0");
                reconciliationItemMapper.insert(item);
                // 更新 DepotItem 对账状态为 "1"（已对账）
                if (item.getBillDetailId() != null) {
                    DepotItem depotItem = depotItemMapper.selectByPrimaryKey(item.getBillDetailId());
                    if (depotItem != null) {
                        headIdSet.add(depotItem.getHeaderId());
                        depotItem.setReconciliationStatus("1");
                        depotItemMapper.updateByPrimaryKeySelective(depotItem);
                    }
                }
            }
        }
        // 5. 按明细实际状态回写 DepotHead 对账状态
        for (Long headId : headIdSet) {
            updateDepotHeadReconciliationStatus(headId);
        }
        return head.getId();
    }

    /**
     * 更新付款/开票状态
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer isPaid, Date payTime,
                             Integer isInvoiced, String invoiceCode, Date invoiceTime) throws Exception {
        User user = userService.getCurrentUser();
        // 首次标记已付款时自动填充付款时间
        if (Integer.valueOf(1).equals(isPaid) && payTime == null) {
            payTime = new Date();
        }
        // 首次标记已开票时自动填充开票时间
        if (Integer.valueOf(1).equals(isInvoiced) && invoiceTime == null) {
            invoiceTime = new Date();
        }
        ReconciliationHead head = new ReconciliationHead();
        head.setId(id);
        head.setIsPaid(isPaid);
        head.setPayTime(payTime);
        head.setIsInvoiced(isInvoiced);
        head.setInvoiceCode(invoiceCode);
        head.setInvoiceTime(invoiceTime);
        head.setLastUpdateBy(user.getId());
        head.setLastUpdateTime(new Date());
        reconciliationHeadMapper.updateByPrimaryKeySelective(head);
    }

    /**
     * 检查是否可删除：仅未付款且未开票时可删除
     */
    public void checkCanDelete(Long id) throws Exception {
        ReconciliationHead head = reconciliationHeadMapper.selectByPrimaryKey(id);
        if (head == null) {
            throw new Exception("对账单不存在");
        }
        if (Integer.valueOf(1).equals(head.getIsPaid())) {
            throw new Exception("已付款的对账单不能删除");
        }
        if (Integer.valueOf(1).equals(head.getIsInvoiced())) {
            throw new Exception("已开票的对账单不能删除");
        }
    }

    /**
     * 逻辑删除对账单 + 回写对账状态
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteBill(Long id) throws Exception {
        checkCanDelete(id);
        User user = userService.getCurrentUser();
        // 1. 查询所有明细行
        ReconciliationItemExample itemExample = new ReconciliationItemExample();
        itemExample.createCriteria()
            .andHeaderIdEqualTo(id)
            .andDeleteFlagNotEqualTo("1");
        List<ReconciliationItem> items = reconciliationItemMapper.selectByExample(itemExample);
        // 2. 逻辑删除主表
        ReconciliationHead head = new ReconciliationHead();
        head.setId(id);
        head.setDeleteFlag("1");
        head.setLastUpdateBy(user.getId());
        head.setLastUpdateTime(new Date());
        reconciliationHeadMapper.updateByPrimaryKeySelective(head);
        // 3. 逻辑删除从表 + 回写明细对账状态
        Set<Long> headIdSet = new HashSet<>();
        for (ReconciliationItem item : items) {
            ReconciliationItem del = new ReconciliationItem();
            del.setId(item.getId());
            del.setDeleteFlag("1");
            reconciliationItemMapper.updateByPrimaryKeySelective(del);
            // 恢复明细为未对账状态
            if (item.getBillDetailId() != null) {
                DepotItem depotItem = depotItemMapper.selectByPrimaryKey(item.getBillDetailId());
                if (depotItem != null) {
                    headIdSet.add(depotItem.getHeaderId());
                    depotItem.setReconciliationStatus("0");
                    depotItemMapper.updateByPrimaryKeySelective(depotItem);
                }
            }
        }
        // 4. 按明细实际状态回写 DepotHead 对账状态
        for (Long headId : headIdSet) {
            updateDepotHeadReconciliationStatus(headId);
        }
    }

    /**
     * 编辑对账单：移除单行明细
     * 1. 逻辑删除该明细行
     * 2. 回写 DepotItem/DepotHead 对账状态
     * 3. 重新计算合计金额
     */
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void removeItem(Long itemId) throws Exception {
        ReconciliationItem item = reconciliationItemMapper.selectByPrimaryKey(itemId);
        if (item == null) throw new Exception("对账明细不存在");

        ReconciliationHead head = reconciliationHeadMapper.selectByPrimaryKey(item.getHeaderId());
        if (head == null) throw new Exception("对账单不存在");
        if (Integer.valueOf(1).equals(head.getIsPaid())) throw new Exception("已付款的对账单不能编辑");
        if (Integer.valueOf(1).equals(head.getIsInvoiced())) throw new Exception("已开票的对账单不能编辑");

        // 1. 逻辑删除明细
        ReconciliationItem del = new ReconciliationItem();
        del.setId(itemId);
        del.setDeleteFlag("1");
        reconciliationItemMapper.updateByPrimaryKeySelective(del);

        // 2. 回写 DepotItem 状态
        if (item.getBillDetailId() != null) {
            DepotItem depotItem = depotItemMapper.selectByPrimaryKey(item.getBillDetailId());
            if (depotItem != null) {
                depotItem.setReconciliationStatus("0");
                depotItemMapper.updateByPrimaryKeySelective(depotItem);
                updateDepotHeadReconciliationStatus(depotItem.getHeaderId());
            }
        }

        // 3. 重新计算主表合计金额
        ReconciliationItemExample example = new ReconciliationItemExample();
        example.createCriteria()
            .andHeaderIdEqualTo(head.getId())
            .andDeleteFlagNotEqualTo("1");
        List<ReconciliationItem> items = reconciliationItemMapper.selectByExample(example);
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (ReconciliationItem ri : items) {
                if (ri.getMaterialAmount() != null) {
                    total = total.add(ri.getMaterialAmount());
                }
            }
        }
        ReconciliationHead update = new ReconciliationHead();
        update.setId(head.getId());
        update.setTotalAmount(total);
        if (items == null || items.isEmpty()) {
            // 所有明细已删除，逻辑删除主表
            update.setDeleteFlag("1");
        }
        reconciliationHeadMapper.updateByPrimaryKeySelective(update);
    }

    /**
     * 查询创建人姓名
     */
    public String getCreatorNameById(Long userId) throws Exception {
        try {
            User user = userService.getUser(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询主表详情
     */
    public ReconciliationHead getHeadById(Long id) throws Exception {
        return reconciliationHeadMapper.selectByPrimaryKey(id);
    }

    /**
     * 查询从表列表
     */
    public List<ReconciliationItem> getItemsByHeadId(Long headId) throws Exception {
        ReconciliationItemExample example = new ReconciliationItemExample();
        example.createCriteria()
            .andHeaderIdEqualTo(headId)
            .andDeleteFlagNotEqualTo("1");
        return reconciliationItemMapper.selectByExample(example);
    }

    private void updateDepotHeadReconciliationStatus(Long depotHeadId) throws Exception {
        if (depotHeadId == null) {
            return;
        }
        DepotItemExample totalExample = new DepotItemExample();
        totalExample.createCriteria()
            .andHeaderIdEqualTo(depotHeadId)
            .andDeleteFlagNotEqualTo("1");
        long totalCount = depotItemMapper.countByExample(totalExample);

        DepotItemExample reconciledExample = new DepotItemExample();
        reconciledExample.createCriteria()
            .andHeaderIdEqualTo(depotHeadId)
            .andReconciliationStatusEqualTo("1")
            .andDeleteFlagNotEqualTo("1");
        long reconciledCount = depotItemMapper.countByExample(reconciledExample);

        String reconciliationStatus = getReconciliationStatus(totalCount, reconciledCount);
        depotHeadService.updateReconciliationStatus(depotHeadId, reconciliationStatus);
        updateLinkedOrderReconciliationStatus(depotHeadId);
    }

    private String getReconciliationStatus(long totalCount, long reconciledCount) {
        if (totalCount <= 0 || reconciledCount <= 0) {
            return "0";
        }
        if (reconciledCount >= totalCount) {
            return "1";
        }
        return "2";
    }

    private void updateLinkedOrderReconciliationStatus(Long sourceHeadId) throws Exception {
        DepotHead sourceHead = depotHeadMapper.selectByPrimaryKey(sourceHeadId);
        if (sourceHead == null || sourceHead.getLinkNumber() == null || sourceHead.getLinkNumber().trim().isEmpty()) {
            return;
        }
        String orderSubType = getLinkedOrderSubType(sourceHead);
        if (orderSubType == null) {
            return;
        }
        DepotHeadExample orderExample = new DepotHeadExample();
        orderExample.createCriteria()
            .andNumberEqualTo(sourceHead.getLinkNumber())
            .andTypeEqualTo("其它")
            .andSubTypeEqualTo(orderSubType)
            .andDeleteFlagNotEqualTo("1");
        List<DepotHead> orderHeads = depotHeadMapper.selectByExample(orderExample);
        if (orderHeads == null || orderHeads.isEmpty()) {
            return;
        }

        DepotHeadExample linkedExample = new DepotHeadExample();
        linkedExample.createCriteria()
            .andLinkNumberEqualTo(sourceHead.getLinkNumber())
            .andTypeEqualTo(sourceHead.getType())
            .andSubTypeEqualTo(sourceHead.getSubType())
            .andDeleteFlagNotEqualTo("1");
        List<DepotHead> linkedHeads = depotHeadMapper.selectByExample(linkedExample);
        String orderStatus = getLinkedOrderStatus(linkedHeads);
        for (DepotHead orderHead : orderHeads) {
            depotHeadService.updateReconciliationStatus(orderHead.getId(), orderStatus);
        }
    }

    private String getLinkedOrderSubType(DepotHead sourceHead) {
        if ("入库".equals(sourceHead.getType()) && "采购".equals(sourceHead.getSubType())) {
            return "采购订单";
        }
        if ("出库".equals(sourceHead.getType()) && "销售".equals(sourceHead.getSubType())) {
            return "销售订单";
        }
        return null;
    }

    private String getLinkedOrderStatus(List<DepotHead> linkedHeads) {
        if (linkedHeads == null || linkedHeads.isEmpty()) {
            return "0";
        }
        long reconciledCount = 0;
        for (DepotHead linkedHead : linkedHeads) {
            if ("2".equals(linkedHead.getReconciliationStatus())) {
                return "2";
            }
            if ("1".equals(linkedHead.getReconciliationStatus())) {
                reconciledCount++;
            }
        }
        return getReconciliationStatus(linkedHeads.size(), reconciledCount);
    }

    /**
     * 查询对账单列表（带分页和条件过滤）
     */
    public List<ReconciliationHead> findList(String organType, Long organId, String billNo,
                                              String beginTime, String endTime,
                                              Integer isPaid, Integer isInvoiced,
                                              String createMonth,
                                              int offset, int pageSize) throws Exception {
        return reconciliationHeadMapperEx.selectByConditionReconciliationHead(
                organType, organId, billNo, beginTime, endTime, isPaid, isInvoiced, createMonth, offset, pageSize);
    }

    /**
     * 查询对账单列表总数
     */
    public long findCount(String organType, Long organId, String billNo,
                           String beginTime, String endTime,
                           Integer isPaid, Integer isInvoiced,
                           String createMonth) throws Exception {
        return reconciliationHeadMapperEx.countsByReconciliationHead(
                organType, organId, billNo, beginTime, endTime, isPaid, isInvoiced, createMonth);
    }
}
