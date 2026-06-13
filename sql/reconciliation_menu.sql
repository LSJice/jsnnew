-- ===================================================
-- 对账单菜单注册
-- 将供应商对账单和客户对账单菜单添加到财务管理模块下
-- ===================================================

-- 财务管理模块下的现有菜单编号：
-- 070402: 收入单
-- 070403: 支出单
-- 070404: 收款单
-- 070405: 付款单
-- 070406: 转账单
-- 070407: 收预付款
-- 新增：070408: 供应商对账单, 070409: 客户对账单

-- 先查出当前最大 id
SET @maxId = (SELECT IFNULL(MAX(id), 0) FROM jsh_function);

-- 供应商对账单菜单（放在财务管理模块下）
INSERT INTO jsh_function (id, number, name, parent_number, url, component, state, sort, enabled, type, push_btn, icon, delete_flag)
VALUES (@maxId + 1, '070408', '供应商对账单', '0704', '/financial/supplier_reconciliation', '/financial/SupplierReconciliationList', '\0', '0500', '\0', '电脑版', '', 'profile', '0');

-- 客户对账单菜单（放在财务管理模块下）
INSERT INTO jsh_function (id, number, name, parent_number, url, component, state, sort, enabled, type, push_btn, icon, delete_flag)
VALUES (@maxId + 2, '070409', '客户对账单', '0704', '/financial/customer_reconciliation', '/financial/CustomerReconciliationList', '\0', '0510', '\0', '电脑版', '', 'profile', '0');

-- 验证插入结果
SELECT id, number, name, parent_number, url, component
FROM jsh_function
WHERE parent_number = '0704'
ORDER BY number;
