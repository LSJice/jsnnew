package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.ReconciliationItem;
import com.jsh.erp.datasource.entities.ReconciliationItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ReconciliationItemMapper {
    long countByExample(ReconciliationItemExample example);

    int deleteByExample(ReconciliationItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ReconciliationItem record);

    int insertSelective(ReconciliationItem record);

    List<ReconciliationItem> selectByExample(ReconciliationItemExample example);

    ReconciliationItem selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ReconciliationItem record, @Param("example") ReconciliationItemExample example);

    int updateByExample(@Param("record") ReconciliationItem record, @Param("example") ReconciliationItemExample example);

    int updateByPrimaryKeySelective(ReconciliationItem record);

    int updateByPrimaryKey(ReconciliationItem record);
}
