<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <div class="table-page-search-wrapper">
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="6" :sm="24">
                <a-form-item label="对账单号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入对账单号" v-model="queryParam.billNo"/>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="客户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select placeholder="请选择客户" showSearch allowClear
                    :filterOption="filterOption"
                    v-model="queryParam.organId"
                    @search="handleSearchCustomer">
                    <div slot="dropdownRender" slot-scope="menu">
                      <v-nodes :vnodes="menu" />
                      <a-divider style="margin: 4px 0;" />
                      <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initCustomer()">
                        <a-icon type="reload" /> 刷新列表
                      </div>
                    </div>
                    <a-select-option v-for="(item,index) in cusList" :key="index" :value="item.id">
                      {{ item.supplier }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="对账区间" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-range-picker
                    style="width:100%"
                    v-model="queryParam.createTimeRange"
                    format="YYYY-MM-DD"
                    :placeholder="['开始时间', '结束时间']"
                    @change="onDateChange"
                  />
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="收款状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select placeholder="全部" v-model="queryParam.isPaid" style="width:100%;">
                    <a-select-option value="">全部</a-select-option>
                    <a-select-option value="0">未收款</a-select-option>
                    <a-select-option value="1">已收款</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="开票状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select placeholder="全部" v-model="queryParam.isInvoiced" style="width:100%;">
                    <a-select-option value="">全部</a-select-option>
                    <a-select-option value="0">未开票</a-select-option>
                    <a-select-option value="1">已开票</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </a-col>
              </span>
              <template v-if="toggleSearchStatus">
                <a-col :md="6" :sm="24">
                  <a-form-item label="创建月份" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-month-picker v-model="queryParam.createMonth" format="YYYY-MM" placeholder="选择创建月份" style="width:100%;"/>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="创建人" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择创建人" showSearch optionFilterProp="children" v-model="queryParam.creator">
                      <a-select-option v-for="(item,index) in userList" :key="index" :value="item.id">
                        {{ item.userName }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </template>
            </a-row>
          </a-form>
        </div>
        <div class="table-operator" style="border-top: 5px solid #eee; padding-top: 10px;">
          <a-button type="primary" icon="plus" @click="handleAdd">新增对账单</a-button>
          <a-button icon="download" @click="handleExport">导出</a-button>
        </div>
        <a-table
          ref="table"
          bordered
          size="middle"
          rowKey="id"
          :columns="columns"
          :dataSource="dataSource"
          :components="handleDrag(columns)"
          :pagination="ipagination"
          :scroll="scroll"
          :loading="loading"
          :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
          @change="handleTableChange">
          <template slot="htmlSlot" slot-scope="text">
            <div v-html="text"></div>
          </template>
          <template slot="paidSlot" slot-scope="text, record">
            <a-switch
              :checked="text === 1"
              :loading="record._paidSwitching"
              @click.stop="handleTogglePaid(record)"
              checkedChildren="已收款" unCheckedChildren="未收款" />
          </template>
          <template slot="invoicedSlot" slot-scope="text, record">
            <a-tag v-if="text === 1" color="green" @click="handleEditInvoice(record)" style="cursor:pointer;">
              已开票
            </a-tag>
            <a-tag v-else color="red" @click="handleEditInvoice(record)" style="cursor:pointer;">
              未开票
            </a-tag>
          </template>
          <template slot="action" slot-scope="text, record">
            <a @click="handleView(record)">查看</a>
            <a-divider type="vertical" />
            <a @click="handleEdit(record)" v-if="record.isPaid !== 1 || record.isInvoiced !== 1">编辑</a>
            <a-divider type="vertical" v-if="record.isPaid !== 1 || record.isInvoiced !== 1" />
            <a-popconfirm
              v-if="record.isPaid !== 1 && record.isInvoiced !== 1"
              title="确定删除吗?"
              @confirm="() => handleDelete(record.id)">
              <a style="color: #ff4d4f;">删除</a>
            </a-popconfirm>
            <a-tooltip v-else title="已收款或已开票的对账单不能编辑删除">
              <span style="color: #ccc;">编辑</span>
            </a-tooltip>
          </template>
        </a-table>
        <customer-reconciliation-create-modal ref="createModal" @ok="loadData"/>
        <reconciliation-view-modal ref="viewModal" @updated="loadData"/>
      </a-card>
    </a-col>
  </a-row>
</template>

<script>
import { JeecgListMixin } from '@/mixins/JeecgListMixin'
import { FinancialListMixin } from './mixins/FinancialListMixin'
import { getAction } from '@/api/manage'
import { getFormatDate, getPrevMonthFormatDate } from '@/utils/util'
import moment from 'moment'
import CustomerReconciliationCreateModal from './modules/CustomerReconciliationCreateModal'
import ReconciliationViewModal from './modules/ReconciliationViewModal'
import XLSX from 'xlsx'
import FileSaver from 'file-saver'

export default {
  name: 'CustomerReconciliationList',
  mixins: [JeecgListMixin, FinancialListMixin],
  components: {
    VNodes: {
      functional: true,
      render: (h, ctx) => ctx.props.vnodes
    },
    CustomerReconciliationCreateModal,
    ReconciliationViewModal
  },
  data() {
    return {
      labelCol: { span: 5 },
      wrapperCol: { span: 18, offset: 1 },
      description: '客户对账单管理',
      queryParam: {
        billNo: '',
        organId: undefined,
        isPaid: '',
        isInvoiced: '',
        creator: undefined,
        createMonth: undefined,
        beginTime: getPrevMonthFormatDate(3),
        endTime: getFormatDate(),
        createTimeRange: [moment(getPrevMonthFormatDate(3)), moment(getFormatDate())]
      },
      defColumns: [
        { title: '操作', dataIndex: 'action', width: 130, fixed: 'left', scopedSlots: { customRender: 'action' } },
        { title: '对账单号', dataIndex: 'billNo', width: 150 },
        { title: '客户名称', dataIndex: 'organName', width: 200 },
        { title: '对账区间', dataIndex: 'beginTime', width: 200,
          customRender: (text, record) => (record.beginTime || '') + ' ~ ' + (record.endTime || '')
        },
        { title: '合计金额', dataIndex: 'totalAmount', width: 120, align: 'right' },
        { title: '收款状态', dataIndex: 'isPaid', width: 100, scopedSlots: { customRender: 'paidSlot' } },
        { title: '开票状态', dataIndex: 'isInvoiced', width: 100, scopedSlots: { customRender: 'invoicedSlot' } },
        { title: '创建人', dataIndex: 'creatorName', width: 80 },
        { title: '创建时间', dataIndex: 'createTime', width: 120 }
      ],
      defDataIndex: ['action', 'billNo', 'organName', 'beginTime', 'totalAmount', 'isPaid', 'isInvoiced', 'creatorName', 'createTime'],
      url: {
        list: '/reconciliation/list',
        delete: '/reconciliation/delete'
      }
    }
  },
  created() {
    this.initCustomer()
    this.initUser()
    this.initColumnsSetting()
  },
  methods: {
    handleAdd() {
      this.$refs.createModal.open()
    },
    handleView(record) {
      this.$refs.viewModal.open(record.id, false)
    },
    handleEdit(record) {
      this.$refs.viewModal.open(record.id, true)
    },
    handleTogglePaid(record) {
      const newPaid = record.isPaid === 1 ? 0 : 1
      const payTime = newPaid === 1 ? moment().format('YYYY-MM-DD') : null
      this.$set(record, '_paidSwitching', true)
      const params = new URLSearchParams()
      params.append('id', record.id)
      params.append('isPaid', newPaid)
      if (payTime) params.append('payTime', payTime)
      const axios = this.$api ? this.$api.axios : this.axios
      axios.put('/reconciliation/updateStatus', params).then(res => {
        if (res && res.code === 200) {
          record.isPaid = newPaid
          record.payTime = payTime
          this.$message.success(newPaid === 1 ? '已标记为已收款' : '已取消收款标记')
        } else {
          this.$message.error(res.data || '操作失败')
        }
      }).finally(() => {
        this.$set(record, '_paidSwitching', false)
      })
    },
    handleEditInvoice(record) {
      const that = this
      let invoiceCodeInput = record.invoiceCode || ''
      let invoiceTimeValue = record.invoiceTime ? moment(record.invoiceTime) : null
      let isInvoicedChecked = record.isInvoiced === 1
      this.$confirm({
        title: '编辑开票信息',
        okText: '确定',
        cancelText: '取消',
        content: h => (
          <div style="margin-top:16px">
            <a-form-item label="发票号">
              <a-input value={invoiceCodeInput} onInput={e => invoiceCodeInput = e.target.value} placeholder="请输入发票号" />
            </a-form-item>
            <a-form-item label="开票时间">
              <a-date-picker value={invoiceTimeValue} onChange={v => invoiceTimeValue = v} format="YYYY-MM-DD" />
            </a-form-item>
            <a-form-item label="开票状态">
              <a-switch checked={isInvoicedChecked} onChange={v => isInvoicedChecked = v}
                checkedChildren="已开票" unCheckedChildren="未开票" />
            </a-form-item>
          </div>
        ),
        onOk: () => {
          const params = new URLSearchParams()
          params.append('id', record.id)
          params.append('isInvoiced', isInvoicedChecked ? 1 : 0)
          params.append('invoiceCode', invoiceCodeInput || '')
          params.append('invoiceTime', invoiceTimeValue ? invoiceTimeValue.format('YYYY-MM-DD') : '')
          const axios = that.$api ? that.$api.axios : that.axios
          return axios.put('/reconciliation/updateStatus', params).then(res => {
            if (res && res.code === 200) {
              record.isInvoiced = isInvoicedChecked ? 1 : 0
              record.invoiceCode = invoiceCodeInput
              record.invoiceTime = invoiceTimeValue ? invoiceTimeValue.format('YYYY-MM-DD') : ''
              that.$message.success('更新成功')
            } else {
              that.$message.error(res.data || '更新失败')
            }
          })
        }
      })
    },
    getQueryParams() {
      let params = {
        organType: '客户'
      }
      if (this.queryParam.billNo) params.billNo = this.queryParam.billNo
      if (this.queryParam.organId != null) params.organId = this.queryParam.organId
      if (this.queryParam.isPaid !== '') params.isPaid = this.queryParam.isPaid
      if (this.queryParam.isInvoiced !== '') params.isInvoiced = this.queryParam.isInvoiced
      if (this.queryParam.creator != null) params.creator = this.queryParam.creator
      if (this.queryParam.createMonth) {
        params.createMonth = this.queryParam.createMonth.format('YYYY-MM')
      }
      if (this.queryParam.beginTime) params.beginTime = this.queryParam.beginTime
      if (this.queryParam.endTime) params.endTime = this.queryParam.endTime
      return { search: JSON.stringify(params), currentPage: this.ipagination.current, pageSize: this.ipagination.pageSize }
    },
    filterOption(input, option) {
      if (!option || !option.componentOptions || !option.componentOptions.children) return false
      const text = option.componentOptions.children[0].text
      return text.toLowerCase().indexOf(input.toLowerCase()) >= 0
    },
    handleExport() {
      this.loading = true
      let queryParams = this.getQueryParams()
      queryParams.pageSize = 9999
      getAction(this.url.list, queryParams).then(res => {
        if (res && res.code === 200) {
          const exportData = []
          res.data.rows.forEach(row => {
            exportData.push({
              '对账单号': row.billNo,
              '客户名称': row.organName,
              '对账开始日期': row.beginTime,
              '对账结束日期': row.endTime,
              '合计金额': row.totalAmount,
              '收款状态': row.isPaid === 1 ? '已收款' : '未收款',
              '收款时间': row.payTime || '',
              '开票状态': row.isInvoiced === 1 ? '已开票' : '未开票',
              '发票号': row.invoiceCode || '',
              '开票时间': row.invoiceTime || '',
              '创建人': row.creatorName || '',
              '创建时间': row.createTime || ''
            })
          })
          const ws = XLSX.utils.json_to_sheet(exportData)
          const wb = XLSX.utils.book_new()
          XLSX.utils.book_append_sheet(wb, ws, '对账单')
          try {
            XLSX.writeFile(wb, '客户对账单.xlsx')
          } catch (e) {
            console.error('导出失败：', e)
            this.$message.error('导出失败：' + (e.message || '未知错误'))
          }
        }
      }).finally(() => { this.loading = false })
    },
    searchReset() {
      this.queryParam = {
        billNo: '',
        organId: undefined,
        isPaid: '',
        isInvoiced: '',
        creator: undefined,
        createMonth: undefined,
        beginTime: getPrevMonthFormatDate(3),
        endTime: getFormatDate(),
        createTimeRange: [moment(getPrevMonthFormatDate(3)), moment(getFormatDate())]
      }
      this.loadData(1)
    }
  }
}
</script>

<style scoped>
@import '~@assets/less/common.less'
</style>
