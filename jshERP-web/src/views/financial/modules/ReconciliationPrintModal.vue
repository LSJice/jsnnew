<template>
  <j-modal
    title="打印预览"
    :visible="visible"
    :width="900"
    @cancel="handleCancel"
    :footer="null"
    switchFullscreen>
    <div ref="printArea" class="print-area">
      <!-- 报表头部 -->
      <div class="report-header">
        <h2>供应商对账单</h2>
        <div class="report-subtitle">Supplier Reconciliation Statement</div>
      </div>

      <!-- 供应商信息 -->
      <div class="customer-info">
        <div class="info-item">
          <div class="label">供应商名称</div>
          <div class="value">{{ head.organName }}</div>
        </div>
        <div class="info-item">
          <div class="label">对账区间</div>
          <div class="value">{{ head.beginTime }} 至 {{ head.endTime }}</div>
        </div>
        <div class="info-item">
          <div class="label">单据编号</div>
          <div class="value">{{ head.billNo }}</div>
        </div>
        <div class="info-item">
          <div class="label">合计金额</div>
          <div class="value highlight-amount">¥ {{ head.totalAmount }}</div>
        </div>
      </div>

      <!-- 本期业务明细 -->
      <h4 class="section-title">本期业务明细</h4>
      <table class="detail-table">
        <thead>
          <tr>
            <th style="width: 50px;">序号</th>
            <th style="width: 130px;">采购单号</th>
            <th>商品名称</th>
            <th style="width: 100px;">规格</th>
            <th style="width: 80px;">数量</th>
            <th style="width: 80px;">单价</th>
            <th style="width: 100px;">金额</th>
            <th style="width: 100px;">应付金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in items" :key="item.id">
            <td class="text-center">{{ index + 1 }}</td>
            <td>{{ item.billNumber }}</td>
            <td class="text-left">{{ item.materialName }}</td>
            <td>{{ item.materialSpec }}</td>
            <td class="text-right">{{ item.materialCount }}</td>
            <td class="text-right">{{ item.materialPrice }}</td>
            <td class="text-right">{{ item.materialAmount }}</td>
            <td class="text-right">{{ item.needDebt }}</td>
          </tr>
          <tr v-if="items.length === 0">
            <td colspan="8" class="text-center" style="padding: 20px; color: #999;">暂无明细数据</td>
          </tr>
          <tr class="total-row">
            <td colspan="4" class="text-right">合计</td>
            <td colspan="2"></td>
            <td class="text-right">{{ totalMaterialAmount }}</td>
            <td class="text-right highlight-amount">{{ totalNeedDebt }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 签字确认区域 -->
      <div class="sign-section">
        <div class="sign-row">
          <div class="sign-box">
            <div class="sign-label">供应商确认：</div>
            <div class="sign-line"></div>
            <div class="sign-hint">（签字/盖章）</div>
          </div>
          <div class="sign-box">
            <div class="sign-label">财务审核：</div>
            <div class="sign-line"></div>
            <div class="sign-hint">（签字）</div>
          </div>
          <div class="sign-box">
            <div class="sign-label">制单人：</div>
            <div class="sign-line"></div>
            <div class="sign-hint">（签字）</div>
          </div>
        </div>
      </div>

      <!-- 页码 -->
      <div class="page-footer">第 1 页 / 共 1 页</div>
    </div>

    <template #footer>
      <a-button v-print="'#printArea'" type="primary" icon="printer">打印</a-button>
    </template>
  </j-modal>
</template>

<script>
  import { getAction } from '@/api/manage'

  export default {
    name: 'ReconciliationPrintModal',
    data() {
      return {
        visible: false,
        head: {},
        items: []
      }
    },
    computed: {
      totalMaterialAmount() {
        const sum = this.items.reduce((s, i) => s + (parseFloat(i.materialAmount) || 0), 0)
        return sum.toFixed(2)
      },
      totalNeedDebt() {
        const sum = this.items.reduce((s, i) => s + (parseFloat(i.needDebt) || 0), 0)
        return sum.toFixed(2)
      }
    },
    methods: {
      open(id) {
        this.visible = true
        this.head = {}
        this.items = []
        this.loadData(id)
      },
      loadData(id) {
        getAction('/reconciliation/head', { id }).then(res => {
          if (res && res.code === 200) {
            this.head = res.data || {}
          }
        })
        getAction('/reconciliation/item', { headId: id }).then(res => {
          if (res && res.code === 200) {
            this.items = (res.data && res.data.rows) || []
          }
        })
      },
      handleCancel() {
        this.visible = false
      }
    }
  }
</script>

<style scoped>
.report-header {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #000;
}
.report-header h2 {
  font-size: 24px;
  font-weight: bold;
  margin: 0;
}
.report-subtitle {
  margin-top: 8px;
  color: #666;
  font-size: 14px;
}
.customer-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 16px;
  background: #f9f9f9;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}
.info-item {
  flex: 1;
  text-align: center;
}
.info-item .label {
  color: #999;
  font-size: 12px;
  margin-bottom: 4px;
}
.info-item .value {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
}
.highlight-amount {
  color: #f5222d;
  font-weight: bold;
}
.section-title {
  margin: 24px 0 12px 0;
  font-size: 15px;
}
.detail-table {
  width: 100%;
  margin-bottom: 20px;
  border-collapse: collapse;
  font-size: 13px;
}
.detail-table th,
.detail-table td {
  border: 1px solid #e8e8e8;
  padding: 8px;
  text-align: center;
}
.detail-table th {
  background: #fafafa;
  font-weight: 600;
  padding: 10px 8px;
}
.text-left {
  text-align: left;
}
.text-right {
  text-align: right;
}
.text-center {
  text-align: center;
}
.total-row {
  background: #f9f9f9;
  font-weight: bold;
}
.sign-section {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}
.sign-row {
  display: flex;
  justify-content: space-around;
  margin-top: 24px;
}
.sign-box {
  text-align: center;
}
.sign-box .sign-label {
  margin-bottom: 8px;
}
.sign-box .sign-line {
  display: inline-block;
  width: 150px;
  border-bottom: 1px solid #000;
  height: 40px;
}
.sign-box .sign-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}
.page-footer {
  text-align: center;
  margin-top: 16px;
  color: #999;
  font-size: 12px;
}

@media print {
  .print-area {
    border: none;
    padding: 0;
  }
}
</style>
