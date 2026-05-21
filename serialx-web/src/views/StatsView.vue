<template>
  <div class="stats-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">调用统计</h1>
        <p class="page-subtitle">多维度分析 ID 发号流量、响应延迟及错误率分布</p>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            size="default"
          />
        </el-form-item>
        <el-form-item label="Namespace">
          <el-select v-model="selectedNs" placeholder="选择 Namespace" clearable>
            <el-option label="order_id" value="order_id" />
            <el-option label="user_id" value="user_id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button>导出报表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never" class="chart-box">
          <template #header><span class="fw-700">QPS 趋势分析</span></template>
          <div class="placeholder-chart">
            <!-- Simulated chart bars -->
            <div v-for="i in 24" :key="i" class="bar" :style="{ height: Math.random() * 80 + 20 + '%' }"></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="chart-box">
          <template #header><span class="fw-700">错误码占比</span></template>
          <div class="placeholder-pie">
            <div class="pie-slice" style="--p:40; --c:var(--accent)">40% 成功</div>
            <div class="pie-slice" style="--p:10; --c:var(--red)">10% 签名错误</div>
            <div class="pie-slice" style="--p:5; --c:var(--amber)">5% 限流</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt-20">
      <template #header><span class="fw-700">调用排行 (Top 10)</span></template>
      <el-table :data="topN" size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="ns" label="Namespace" />
        <el-table-column prop="app" label="调用应用" />
        <el-table-column prop="qps" label="平均 QPS" sortable />
        <el-table-column prop="latency" label="P99 延迟" sortable>
          <template #default="{ row }">{{ row.latency }}ms</template>
        </el-table-column>
        <el-table-column prop="errorRate" label="错误率">
          <template #default="{ row }">
            <el-text :type="row.errorRate > 1 ? 'danger' : 'success'">{{ row.errorRate }}%</el-text>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const timeRange = ref([])
const selectedNs = ref('')
const topN = [
  { ns: 'order_id', app: 'OrderService', qps: 1205, latency: 1.5, errorRate: 0.01 },
  { ns: 'user_id', app: 'UserCenter', qps: 850, latency: 0.8, errorRate: 0.05 },
  { ns: 'payment_sn', app: 'PayGateway', qps: 420, latency: 2.1, errorRate: 0.12 },
  { ns: 'trace_id', app: 'EagleEye', qps: 5500, latency: 0.4, errorRate: 0.00 },
]
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 700; }
.page-subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }
.filter-card { margin-bottom: 20px; }
.chart-box { height: 320px; }
.placeholder-chart { height: 200px; display: flex; align-items: flex-end; gap: 4px; padding-top: 40px; }
.bar { flex: 1; background: var(--accent-soft); border-radius: 2px; }
.placeholder-pie { height: 200px; display: flex; flex-direction: column; justify-content: center; gap: 12px; }
.pie-slice { font-size: 12px; font-weight: 600; padding: 4px 12px; border-left: 4px solid var(--c); background: var(--bg-card-alt); border-radius: 4px; }
</style>
