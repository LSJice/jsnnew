package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.ReconciliationHead;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReconciliationHeadMapperEx {

    List<ReconciliationHead> selectByConditionReconciliationHead(
            @Param("organType") String organType,
            @Param("organId") Long organId,
            @Param("billNo") String billNo,
            @Param("beginTime") String beginTime,
            @Param("endTime") String endTime,
            @Param("isPaid") Integer isPaid,
            @Param("isInvoiced") Integer isInvoiced,
            @Param("createMonth") String createMonth,
            @Param("offset") Integer offset,
            @Param("rows") Integer rows);

    Long countsByReconciliationHead(
            @Param("organType") String organType,
            @Param("organId") Long organId,
            @Param("billNo") String billNo,
            @Param("beginTime") String beginTime,
            @Param("endTime") String endTime,
            @Param("isPaid") Integer isPaid,
            @Param("isInvoiced") Integer isInvoiced,
            @Param("createMonth") String createMonth);
}
