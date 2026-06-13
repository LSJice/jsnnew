-- ===================================================
-- 对账功能 DDL
-- ===================================================

-- 1. 对账单主表
CREATE TABLE IF NOT EXISTS `jsh_reconciliation_head` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bill_no` VARCHAR(50) NOT NULL COMMENT '对账单号（自动生成，前缀DZ）',
  `organ_type` VARCHAR(20) DEFAULT '供应商' COMMENT '往来类型（供应商/客户）',
  `organ_id` BIGINT COMMENT '供应商/客户ID',
  `organ_name` VARCHAR(100) COMMENT '供应商/客户名称',
  `begin_time` DATETIME COMMENT '对账区间-开始',
  `end_time` DATETIME COMMENT '对账区间-结束',
  `total_amount` DECIMAL(24,6) DEFAULT 0 COMMENT '合计金额（自动累加明细行）',
  `is_paid` TINYINT DEFAULT 0 COMMENT '是否付款/收款（0=未，1=已）',
  `pay_time` DATETIME COMMENT '付款/收款时间',
  `is_invoiced` TINYINT DEFAULT 0 COMMENT '是否开票（0=未开票，1=已开票）',
  `invoice_code` VARCHAR(100) COMMENT '发票编码',
  `invoice_time` DATETIME COMMENT '开票时间',
  `creator` BIGINT COMMENT '创建人',
  `create_time` DATETIME COMMENT '创建时间',
  `last_update_by` BIGINT COMMENT '最后修改人',
  `last_update_time` DATETIME COMMENT '最后修改时间',
  `remark` VARCHAR(500) COMMENT '备注',
  `template_id` BIGINT COMMENT '模板ID（null=系统默认模板）',
  `tenant_id` BIGINT COMMENT '租户ID',
  `delete_flag` VARCHAR(1) DEFAULT '0' COMMENT '删除标记（0=正常，1=已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_organ_type_id` (`organ_type`, `organ_id`),
  KEY `idx_bill_no` (`bill_no`),
  KEY `idx_begin_end_time` (`begin_time`, `end_time`),
  KEY `idx_tenant_delete` (`tenant_id`, `delete_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='对账单主表（供应商/客户共用）';

-- 2. 对账单明细表
CREATE TABLE IF NOT EXISTS `jsh_reconciliation_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `header_id` BIGINT NOT NULL COMMENT '主表ID',
  `bill_number` VARCHAR(50) COMMENT '入库/出库单号',
  `bill_id` BIGINT COMMENT '入库/出库单ID（关联 jsh_depot_head.id）',
  `organ_id` BIGINT COMMENT '供应商/客户ID',
  `organ_name` VARCHAR(100) COMMENT '供应商/客户名称',
  `material_id` BIGINT COMMENT '商品ID',
  `material_name` VARCHAR(200) COMMENT '品名',
  `material_spec` VARCHAR(200) COMMENT '规格',
  `material_unit` VARCHAR(50) COMMENT '单位',
  `material_count` DECIMAL(24,6) DEFAULT 0 COMMENT '数量',
  `material_price` DECIMAL(24,6) DEFAULT 0 COMMENT '单价',
  `material_amount` DECIMAL(24,6) DEFAULT 0 COMMENT '金额（数量×单价）',
  `need_debt` DECIMAL(24,6) DEFAULT 0 COMMENT '应付/应收金额（净额口径）',
  `remark` VARCHAR(500) COMMENT '备注',
  `tenant_id` BIGINT COMMENT '租户ID',
  `delete_flag` VARCHAR(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_header_id` (`header_id`),
  KEY `idx_bill_number` (`bill_number`),
  KEY `idx_tenant_delete` (`tenant_id`, `delete_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='对账单明细表（供应商/客户共用）';

-- 3. 对账模板配置表（扩展预留）
CREATE TABLE IF NOT EXISTS `jsh_reconciliation_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `supplier_type` VARCHAR(20) NOT NULL COMMENT '适用类型（供应商/客户）',
  `columns_config` JSON COMMENT '列配置',
  `layout_config` JSON COMMENT '版式配置',
  `formula_config` JSON COMMENT '计算公式配置',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认模板',
  `tenant_id` BIGINT COMMENT '租户ID',
  `delete_flag` VARCHAR(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_supplier_type` (`supplier_type`),
  KEY `idx_tenant_delete` (`tenant_id`, `delete_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='对账模板配置表';

-- 预置默认模板
INSERT IGNORE INTO `jsh_reconciliation_template` (`name`, `supplier_type`, `is_default`, `tenant_id`)
VALUES ('默认供应商对账模板', '供应商', 1, 0);
INSERT IGNORE INTO `jsh_reconciliation_template` (`name`, `supplier_type`, `is_default`, `tenant_id`)
VALUES ('默认客户对账模板', '客户', 1, 0);

-- 4. DepotHead 新增对账状态字段
ALTER TABLE `jsh_depot_head`
  ADD COLUMN `reconciliation_status` VARCHAR(1) DEFAULT '0' COMMENT '对账状态（0=未对账，1=已对账，2=部分对账）';

-- 5. DepotItem 新增对账状态字段（对账明细回写用）
ALTER TABLE `jsh_depot_item`
  ADD COLUMN `reconciliation_status` VARCHAR(1) DEFAULT '0' COMMENT '对账状态（0=未对账，1=已对账）';
