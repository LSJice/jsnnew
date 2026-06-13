<template>
  <j-modal
    :title="isEdit ? '编辑对账单' : '对账单详情'"
    :visible="visible"
    :width="1200"
    @cancel="handleCancel"
    :footer="null"
    switchFullscreen>
    <a-spin :spinning="loading">
      <template v-if="head">
        <a-descriptions bordered size="small" :column="3">
          <a-descriptions-item label="对账单号">{{ head.billNo }}</a-descriptions-item>
          <a-descriptions-item label="供应商/客户">{{ head.organName }}</a-descriptions-item>
          <a-descriptions-item label="对账区间">{{ head.beginTime }} ~ {{ head.endTime }}</a-descriptions-item>
          <a-descriptions-item label="合计金额">{{ head.totalAmount }}</a-descriptions-item>
          <a-descriptions-item label="付款状态">
            <a-tag :color="head.isPaid === 1 ? 'green' : 'red'">{{ head.isPaid === 1 ? '已付款' : '未付款' }}</a-tag>
            <span v-if="head.payTime">（付款时间：{{ head.payTime }}）</span>
          </a-descriptions-item>
          <a-descriptions-item label="开票状态">
            <a-tag :color="head.isInvoiced === 1 ? 'green' : 'red'">{{ head.isInvoiced === 1 ? '已开票' : '未开票' }}</a-tag>
            <span v-if="head.invoiceCode">（发票号：{{ head.invoiceCode }}）</span>
            <span v-if="head.invoiceTime">（开票时间：{{ head.invoiceTime }}）</span>
          </a-descriptions-item>
          <a-descriptions-item label="创建人">{{ head.creatorName }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ head.createTime }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ head.remark || '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-divider />
        <h4>对账明细</h4>
        <a-table
          :columns="itemColumns"
          :dataSource="items"
          rowKey="id"
          :pagination="false"
          size="small"
          bordered
          :scroll="{ x: 1300 }"
          style="margin-top: 16px;">
          <template slot="action" slot-scope="text, record" v-if="isEdit">
            <a-popconfirm
              title="确定移除此明细吗?"
              @confirm="() => handleRemoveItem(record.id)">
              <a style="color: #ff4d4f;">移除</a>
            </a-popconfirm>
          </template>
        </a-table>

        <div style="margin-top: 24px; text-align: right;">
          <a-button type="primary" @click="handleMarkPaid" v-if="isEdit && head.isPaid !== 1">标记已付款</a-button>
          <a-button @click="handleEditInvoice" v-if="isEdit" style="margin-left: 8px;">编辑开票信息</a-button>
        </div>
      </template>
    </a-spin>
  </j-modal>
</template>

<script>
import { getAction } from '@/api/manage'
import moment from 'moment'

export default {
  name: 'ReconciliationViewModal',
  data() {
    return {
      visible: false,
      isEdit: false,
      loading: false,
      head: null,
      items: [],
      payTimeValue: null,
      invoiceCodeValue: '',
      invoiceTimeValue: null,
      itemColumns: []
    }
  },
  created() {
    this.buildColumns(false)
  },
  watch: {
    isEdit(val) {
      this.buildColumns(val)
    }
  },
  methods: {
    open(id, isEdit) {
      this.visible = true
      this.isEdit = !!isEdit
      this.head = null
      this.items = []
      this.loadHead(id)
      this.loadItems(id)
    },
    buildColumns(isEdit) {
      const cols = [
        { title: '单号', dataIndex: 'billNumber', width: 150 },
        { title: '商品名称', dataIndex: 'materialName', width: 150 },
        { title: '规格型号', dataIndex: 'materialSpec', width: 120 },
        { title: '单位', dataIndex: 'materialUnit', width: 60 },
        { title: '数量', dataIndex: 'materialCount', width: 80, align: 'right' },
        { title: '单价', dataIndex: 'materialPrice', width: 80, align: 'right' },
        { title: '金额', dataIndex: 'materialAmount', width: 100, align: 'right' },
        { title: '欠款', dataIndex: 'needDebt', width: 100, align: 'right' },
        { title: '备注', dataIndex: 'remark', width: 150 }
      ]
      if (isEdit) {
        cols.push({ title: '操作', width: 60, fixed: 'right', scopedSlots: { customRender: 'action' } })
      }
      this.itemColumns = cols
    },
    loadHead(id) {
      this.loading = true
      getAction('/reconciliation/head', { id }).then(res => {
        if (res && res.code === 200) {
          this.head = res.data
        }
      }).finally(() => {
        this.loading = false
      })
    },
    loadItems(id) {
      getAction('/reconciliation/item', { headId: id }).then(res => {
        if (res && res.code === 200) {
          this.items = res.data.rows || []
        }
      })
    },
    handleMarkPaid() {
      const that = this
      this.$confirm({
        title: '标记已付款',
        content: h => (
          <a-form-item label="付款时间">
            <a-date-picker value={that.payTimeValue} onChange={v => that.payTimeValue = v} format="YYYY-MM-DD" />
          </a-form-item>
        ),
        onOk: () => {
          const params = new URLSearchParams()
          params.append('id', that.head.id)
          params.append('isPaid', 1)
          if (that.payTimeValue) {
            params.append('payTime', that.payTimeValue.format('YYYY-MM-DD'))
          }
          const axios = that.$api ? that.$api.axios : that.axios
          return axios.put('/reconciliation/updateStatus', params).then(res => {
            if (res && res.code === 200) {
              that.head.isPaid = 1
              if (that.payTimeValue) that.head.payTime = that.payTimeValue.format('YYYY-MM-DD')
              that.$message.success('已标记为已付款')
              that.$emit('updated')
            } else {
              that.$message.error(res.data || '操作失败')
            }
          })
        }
      })
    },
    handleEditInvoice() {
      const that = this
      this.$confirm({
        title: '编辑开票信息',
        content: h => (
          <div>
            <a-form-item label="发票号">
              <a-input value={that.invoiceCodeValue} onInput={e => that.invoiceCodeValue = e.target.value} placeholder="请输入发票号" />
            </a-form-item>
            <a-form-item label="开票时间">
              <a-date-picker value={that.invoiceTimeValue} onChange={v => that.invoiceTimeValue = v} format="YYYY-MM-DD" />
            </a-form-item>
          </div>
        ),
        onOk: () => {
          const params = new URLSearchParams()
          params.append('id', that.head.id)
          params.append('isInvoiced', 1)
          if (that.invoiceCodeValue) params.append('invoiceCode', that.invoiceCodeValue)
          if (that.invoiceTimeValue) params.append('invoiceTime', that.invoiceTimeValue.format('YYYY-MM-DD'))
          const axios = that.$api ? that.$api.axios : that.axios
          return axios.put('/reconciliation/updateStatus', params).then(res => {
            if (res && res.code === 200) {
              that.head.isInvoiced = 1
              if (that.invoiceCodeValue) that.head.invoiceCode = that.invoiceCodeValue
              if (that.invoiceTimeValue) that.head.invoiceTime = that.invoiceTimeValue.format('YYYY-MM-DD')
              that.$message.success('更新成功')
              that.$emit('updated')
            } else {
              that.$message.error(res.data || '更新失败')
            }
          })
        }
      })
    },
    handleRemoveItem(itemId) {
      const that = this
      const params = new URLSearchParams()
      params.append('itemId', itemId)
      const axios = that.$api ? that.$api.axios : that.axios
      axios.delete('/reconciliation/removeItem', { params }).then(res => {
        if (res && res.code === 200) {
          that.$message.success('移除成功')
          that.items = that.items.filter(item => item.id !== itemId)
          that.$emit('updated')
        } else {
          that.$message.error(res && res.data ? res.data : '移除失败')
        }
      })
    },
    handleCancel() {
      this.visible = false
    }
  }
}
</script>
