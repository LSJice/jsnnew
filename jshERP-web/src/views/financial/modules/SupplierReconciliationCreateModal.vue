<template>
  <j-modal
    title="新增供应商对账单"
    :visible="visible"
    :width="1400"
    @cancel="handleCancel"
    :footer="null"
    switchFullscreen>
    <a-form layout="inline">
      <a-form-item label="供应商" required>
        <a-select v-model="form.organId" placeholder="请选择供应商" style="width: 300px;"
          showSearch :filterOption="filterOption" @search="handleSearchSupplier">
          <div slot="dropdownRender" slot-scope="menu">
            <v-nodes :vnodes="menu" />
            <a-divider style="margin: 4px 0;" />
            <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initSupplierList()">
              <a-icon type="reload" /> 刷新列表
            </div>
          </div>
          <a-select-option v-for="item in supList" :key="item.id" :value="item.id">{{ item.supplier }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="对账区间" required>
        <a-range-picker v-model="dateRange" style="width: 240px;"/>
      </a-form-item>
      <a-form-item label="订单状态">
        <a-select v-model="form.statusFilter" style="width: 160px;">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="done">已完全交货</a-select-option>
          <a-select-option value="partial">部分交货</a-select-option>
          <a-select-option value="none">未交货</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="handlePreview" :loading="previewLoading">查询预览</a-button>
      </a-form-item>
    </a-form>

    <template v-if="previewData">
      <!-- 汇总信息 -->
      <a-row :gutter="16" style="margin-top: 16px;">
        <a-col :span="6">
          <a-card><a-statistic title="期初应付余额" :value="previewData.beginAmount" :precision="2" prefix="¥"/></a-card>
        </a-col>
        <a-col :span="6">
          <a-card><a-statistic title="本期发生应付" :value="previewData.periodAmount" :precision="2" prefix="¥"/></a-card>
        </a-col>
        <a-col :span="6">
          <a-card><a-statistic title="本期已付" :value="previewData.changeAmount" :precision="2" prefix="¥"/></a-card>
        </a-col>
        <a-col :span="6">
          <a-card><a-statistic title="期末应付余额" :value="previewData.endAmount" :precision="2" prefix="¥"/></a-card>
        </a-col>
      </a-row>

      <!-- 明细列表 -->
      <div style="margin-top: 16px;">
        <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
          <a-alert type="info" style="flex:1;">
            <template slot="description">
              当前共 <strong>{{ items.length }}</strong> 条入库明细，汇总金额会随明细变化自动更新
            </template>
          </a-alert>
          <a-button icon="download" @click="handleExportDetail" :disabled="items.length === 0" style="margin-left: 16px;">
            导出明细
          </a-button>
        </div>
        <a-table
          :columns="detailColumns"
          :dataSource="items"
          rowKey="id"
          :pagination="false"
          size="small"
          bordered
          :scroll="{ x: 1600 }">
          <template slot="amountSlot" slot-scope="text">
            <span class="amount">{{ formatAmount(text) }}</span>
          </template>
          <template slot="action" slot-scope="text, record, index">
            <a @click="handleRemoveItem(index)" style="color: #ff4d4f;">删除</a>
          </template>
        </a-table>
      </div>

      <div style="margin-top: 16px; text-align: right;">
        <a-button type="primary" @click="handleSave" :loading="saveLoading" :disabled="items.length === 0">保存对账单</a-button>
      </div>
    </template>
  </j-modal>
</template>

<script>
  import { getAction } from '@/api/manage'
  import { findBySelectSup } from '@/api/api'
  import qs from 'qs'
  import { axios } from '@/utils/request'
  import * as XLSX from 'xlsx'
  import FileSaver from 'file-saver'

  const VNodes = {
    functional: true,
    render: (h, ctx) => ctx.props.vnodes
  }

  export default {
    name: 'SupplierReconciliationCreateModal',
    components: {
      VNodes
    },
    data() {
      return {
        visible: false,
        previewLoading: false,
        saveLoading: false,
        setTimeFlag: null,
        supList: [],
        form: {
          organId: null,
          statusFilter: ''
        },
        dateRange: [],
        previewData: null,
        items: [],
        detailColumns: [
          { title: '序号', width: 60, customRender: (text, record, index) => index + 1 },
          { title: '单号', dataIndex: 'billNumber', width: 150 },
          { title: '单据类型', width: 100, customRender: (text, record) => (record.billType || '') + (record.billSubType || '') },
          { title: '商品名称', dataIndex: 'materialName', width: 150 },
          { title: '规格型号', dataIndex: 'materialModel', width: 120 },
          { title: '单位', dataIndex: 'materialUnit', width: 60 },
          { title: '数量', dataIndex: 'operNumber', width: 80, align: 'right' },
          { title: '单价', dataIndex: 'unitPrice', width: 80, align: 'right', scopedSlots: { customRender: 'amountSlot' } },
          { title: '金额', dataIndex: 'allPrice', width: 100, align: 'right', scopedSlots: { customRender: 'amountSlot' } },
          { title: '已付金额', dataIndex: 'changeAmount', width: 100, align: 'right', scopedSlots: { customRender: 'amountSlot' } },
          { title: '入库时间', dataIndex: 'operTime', width: 120,
            customRender: (text) => text ? text.substring(0, 10) : '' },
          { title: '备注', dataIndex: 'billRemark', width: 180 },
          { title: '操作', width: 60, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    created() {
      this.initSupplierList()
    },
    methods: {
      open() {
        this.visible = true
        this.form = { organId: null, statusFilter: '' }
        this.dateRange = []
        this.previewData = null
        this.items = []
      },
      initSupplierList() {
        findBySelectSup({limit: 1}).then(res => {
          if (res) {
            this.supList = res || []
          }
        })
      },
      handlePreview() {
        if (!this.form.organId) {
          this.$message.warning('请选择供应商')
          return
        }
        if (!this.dateRange || this.dateRange.length < 2) {
          this.$message.warning('请选择对账区间')
          return
        }
        this.previewLoading = true
        getAction('/reconciliation/getPreview', {
          organType: '供应商',
          organId: this.form.organId,
          beginTime: this.dateRange[0].format('YYYY-MM-DD'),
          endTime: this.dateRange[1].format('YYYY-MM-DD'),
          statusFilter: this.form.statusFilter
        }).then(res => {
          if (res && res.code === 200) {
            this.items = res.data.items || []
            // 从明细数据直接计算汇总，不使用后端返回的summary
            this.calculateSummaryFromItems()
          }
        }).finally(() => {
          this.previewLoading = false
        })
      },
      calculateSummaryFromItems() {
        // 从明细数据直接计算汇总
        let periodAmount = 0
        let changeAmount = 0
        this.items.forEach(item => {
          periodAmount += Number(item.allPrice || 0)
          changeAmount += Math.abs(Number(item.changeAmount || 0))
        })
        // 期初余额暂时设为0，后续可以通过接口获取
        const beginAmount = 0
        // 期末余额 = 期初 + 本期发生 - 本期已付
        const endAmount = beginAmount + periodAmount - changeAmount
        this.previewData = {
          beginAmount: beginAmount,
          periodAmount: periodAmount,
          changeAmount: changeAmount,
          endAmount: endAmount
        }
      },
      handleRemoveItem(index) {
        const removedItem = this.items[index]
        this.items.splice(index, 1)
        // 重新计算汇总
        this.recalculateSummary()
        this.$message.success('已删除明细：' + removedItem.materialName)
      },
      recalculateSummary() {
        // 删除后重新计算汇总
        this.calculateSummaryFromItems()
      },
      formatAmount(val) {
        if (!val && val !== 0) return '0.00'
        return Number(Math.abs(val)).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      },
      handleSave() {
        if (this.items.length === 0) {
          this.$message.warning('无明细数据可保存')
          return
        }
        // 计算付款状态
        let totalAmount = this.previewData.periodAmount
        let changeAmount = this.previewData.changeAmount
        let isPaid = 0 // 默认未付款
        if (totalAmount > 0 && changeAmount >= totalAmount) {
          isPaid = 1 // 已付款
        }
        // 如果需要支持部分付款，后端需要扩展 isPaid 字段支持 2=部分付款

        // 获取供应商名称
        const sup = this.supList.find(s => s.id === this.form.organId)
        const organName = sup ? sup.supplier : ''

        this.saveLoading = true
        const head = {
          organType: '供应商',
          organId: this.form.organId,
          organName: organName,
          beginTime: this.dateRange[0].format('YYYY-MM-DD'),
          endTime: this.dateRange[1].format('YYYY-MM-DD'),
          initialBalance: this.previewData.beginAmount,
          totalAmount: this.previewData.periodAmount,
          changeAmount: this.previewData.changeAmount,
          finalBalance: this.previewData.endAmount,
          isPaid: isPaid
        }
        const items = this.items.map(item => ({
          billId: item.headerId || item.depotId,
          billDetailId: item.id,
          billType: item.billType,
          billNumber: item.billNumber,
          linkNumber: item.linkNumber,
          billTime: item.operTime,
          materialId: item.materialId,
          materialName: item.materialName,
          materialSpec: item.materialModel,
          materialUnit: item.materialUnit,
          materialCount: item.operNumber,
          materialPrice: item.unitPrice,
          materialAmount: item.allPrice,
          needDebt: (Number(item.allPrice || 0) - Math.abs(Number(item.changeAmount || 0))).toFixed(6),
          remark: item.billRemark
        }))
        const params = new URLSearchParams()
        params.append('head', JSON.stringify(head))
        params.append('items', JSON.stringify(items))
        axios.post('/reconciliation/create', params)
          .then(res => {
            if (res && res.code === 200) {
              this.$message.success('创建成功')
              this.visible = false
              this.$emit('ok')
            } else {
              this.$message.error(res && res.data ? res.data : '创建失败')
            }
          }).catch(err => {
            this.$message.error('创建失败：' + (err.message || '未知错误'))
          }).finally(() => {
            this.saveLoading = false
          })
      },
      handleCancel() {
        this.visible = false
      },
      filterOption(input, option) {
        if (!option || !option.componentOptions || !option.componentOptions.children) return false
        const text = option.componentOptions.children[0].text
        return text.toLowerCase().indexOf(input.toLowerCase()) >= 0
      },
      handleSearchSupplier(value) {
        let that = this
        if (this.setTimeFlag != null) clearTimeout(this.setTimeFlag)
        this.setTimeFlag = setTimeout(() => {
          findBySelectSup({ key: value, limit: 1 }).then(res => {
            if (res) that.supList = res
          })
        }, 500)
      },
      handleExportDetail() {
        try {
          const exportData = this.items.map((item, index) => ({
            '序号': index + 1,
            '单号': item.billNumber || '',
            '商品名称': item.materialName || '',
            '规格型号': item.materialModel || '',
            '单位': item.materialUnit || '',
            '数量': item.operNumber,
            '单价': item.unitPrice,
            '金额': item.allPrice,
            '已付金额': item.changeAmount,
            '入库时间': item.operTime ? item.operTime.substring(0, 10) : '',
            '备注': item.billRemark || ''
          }))
          const ws = XLSX.utils.json_to_sheet(exportData)
          const wb = XLSX.utils.book_new()
          XLSX.utils.book_append_sheet(wb, ws, '对账明细')
          XLSX.writeFile(wb, '对账明细.xlsx')
        } catch (e) {
          console.error('导出明细失败：', e)
          this.$message.error('导出失败：' + (e.message || '未知错误'))
        }
      }
    }
  }
</script>

<style scoped>
.amount {
  font-family: 'Courier New', monospace;
  font-weight: 600;
}
.dropdown-btn {
  padding: 4px 8px;
  cursor: pointer;
  text-align: center;
}
.dropdown-btn:hover {
  background-color: #e6f7ff;
}
</style>
