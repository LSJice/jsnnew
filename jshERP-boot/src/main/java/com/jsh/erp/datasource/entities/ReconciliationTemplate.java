package com.jsh.erp.datasource.entities;

public class ReconciliationTemplate {
    private Long id;

    private String name;

    private String supplierType;

    private String columnsConfig;

    private String layoutConfig;

    private String formulaConfig;

    private Integer isDefault;

    private Long tenantId;

    private String deleteFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getSupplierType() {
        return supplierType;
    }

    public void setSupplierType(String supplierType) {
        this.supplierType = supplierType == null ? null : supplierType.trim();
    }

    public String getColumnsConfig() {
        return columnsConfig;
    }

    public void setColumnsConfig(String columnsConfig) {
        this.columnsConfig = columnsConfig == null ? null : columnsConfig.trim();
    }

    public String getLayoutConfig() {
        return layoutConfig;
    }

    public void setLayoutConfig(String layoutConfig) {
        this.layoutConfig = layoutConfig == null ? null : layoutConfig.trim();
    }

    public String getFormulaConfig() {
        return formulaConfig;
    }

    public void setFormulaConfig(String formulaConfig) {
        this.formulaConfig = formulaConfig == null ? null : formulaConfig.trim();
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(String deleteFlag) {
        this.deleteFlag = deleteFlag == null ? null : deleteFlag.trim();
    }
}
