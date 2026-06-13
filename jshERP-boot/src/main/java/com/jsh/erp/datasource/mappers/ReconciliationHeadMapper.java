package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.ReconciliationHead;
import com.jsh.erp.datasource.entities.ReconciliationHeadExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ReconciliationHeadMapper {
    long countByExample(ReconciliationHeadExample example);

    int deleteByExample(ReconciliationHeadExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ReconciliationHead record);

    int insertSelective(ReconciliationHead record);

    List<ReconciliationHead> selectByExample(ReconciliationHeadExample example);

    ReconciliationHead selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ReconciliationHead record, @Param("example") ReconciliationHeadExample example);

    int updateByExample(@Param("record") ReconciliationHead record, @Param("example") ReconciliationHeadExample example);

    int updateByPrimaryKeySelective(ReconciliationHead record);

    int updateByPrimaryKey(ReconciliationHead record);
}
