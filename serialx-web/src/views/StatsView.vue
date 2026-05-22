<template>
  <div class="stats-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">调用统计</h1>
        <p class="page-subtitle">查看集群发号流量、Service 负载、Namespace 和调用方分布</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="handleQuery">刷新</el-button>
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
          />
        </el-form-item>
        <el-form-item label="Namespace">
          <el-select
            v-model="selectedNamespace"
            placeholder="全部 Namespace"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="namespace in namespaceOptions"
              :key="namespace.namespaceCode"
              :label="namespace.namespaceCode"
              :value="namespace.namespaceCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实例">
          <el-select
            v-model="selectedInstance"
            placeholder="全部实例"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="instance in instanceOptions"
              :key="instance.instanceId"
              :label="instance.instanceId"
              :value="instance.instanceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调用方">
          <el-input v-model="clientName" placeholder="clientName" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">
            查询
          </el-button>
          <el-button :icon="Download" :disabled="!stats" @click="handleExport">导出报表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="20" class="overview-row">
      <el-col v-for="item in overviewItems" :key="item.label" :xs="12" :sm="8" :lg="4">
        <el-card shadow="never" class="stat-mini-card">
          <div>
            <div class="stat-val">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
          <div class="stat-extra">{{ item.extra }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-box" v-loading="loading">
          <template #header>
            <div class="chart-header">
              <span class="fw-700">分钟级调用趋势</span>
              <div class="trend-legend">
                <span class="legend-item"><i class="legend-dot success"></i>成功</span>
                <span class="legend-item"><i class="legend-dot failed"></i>失败</span>
                <span class="legend-peak">峰值 {{ formatNumber(trendPeak) }} 次/分钟</span>
              </div>
            </div>
          </template>
          <div v-if="trendBars.length" class="trend-chart">
            <div class="trend-grid">
              <div class="grid-line"></div>
              <div class="grid-line"></div>
              <div class="grid-line"></div>
              <div class="grid-line"></div>
            </div>
            <div class="trend-bars">
              <div
                v-for="point in trendBars"
                :key="point.time"
                class="trend-bar-item"
                :class="{ active: point.totalRequests > 0 }"
              >
                <div class="trend-tooltip">
                  <div class="tooltip-time">{{ point.fullLabel }}</div>
                  <div>总请求：{{ formatNumber(point.totalRequests) }}</div>
                  <div>成功：{{ formatNumber(point.successRequests) }}</div>
                  <div>失败：{{ formatNumber(point.failedRequests) }}</div>
                  <div>平均延迟：{{ formatNumber(point.avgLatencyMs) }}ms</div>
                </div>
                <div class="trend-bar-track">
                  <div class="trend-stack" :style="{ height: `${point.totalHeight}%` }">
                    <div class="trend-segment failed" :style="{ height: `${point.failedRatio}%` }"></div>
                    <div
                      class="trend-segment success"
                      :style="{ height: `${point.successRatio}%` }"
                    ></div>
                  </div>
                </div>
                <div class="trend-bar-label" :class="{ muted: !point.showLabel }">
                  {{ point.showLabel ? point.label : '' }}
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无调用数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="chart-box" v-loading="loading">
          <template #header><span class="fw-700">失败分布</span></template>
          <div v-if="stats?.errors.length" class="error-list">
            <div v-for="item in stats.errors" :key="item.errorCode" class="error-item">
              <div class="error-main">
                <span class="error-code">{{ item.errorCode }}</span>
                <span class="error-count">{{ item.count }} 次</span>
              </div>
              <el-progress
                :percentage="item.percentage"
                :stroke-width="8"
                :show-text="false"
                status="exception"
              />
            </div>
          </div>
          <el-empty v-else description="暂无失败调用" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt-20" v-loading="loading">
      <template #header><span class="fw-700">Service 实例负载</span></template>
      <el-table :data="stats?.instances || []" size="small" empty-text="暂无实例统计">
        <el-table-column prop="instanceId" label="实例 ID" min-width="190" />
        <el-table-column prop="host" label="主机" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.runtimeStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.runtimeStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentQps" label="QPS" width="90" sortable />
        <el-table-column prop="requestCount" label="请求数" width="110" sortable />
        <el-table-column prop="failedCount" label="失败数" width="100" sortable />
        <el-table-column label="错误率" width="100" sortable>
          <template #default="{ row }">{{ row.errorRate }}%</template>
        </el-table-column>
        <el-table-column label="负载率" width="110" sortable>
          <template #default="{ row }">
            <el-text :type="loadRateType(row.loadRate)">{{ formatLoadRate(row.loadRate) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="avgLatencyMs" label="平均延迟" width="110" sortable>
          <template #default="{ row }">{{ row.avgLatencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="maxLatencyMs" label="最大延迟" width="110" sortable>
          <template #default="{ row }">{{ row.maxLatencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="namespaceCount" label="Namespace" width="110" />
        <el-table-column prop="laggingNamespaceCount" label="版本落后" width="110" />
      </el-table>
    </el-card>

    <el-row :gutter="20" class="mt-20">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" v-loading="loading">
          <template #header><span class="fw-700">Namespace Top 10</span></template>
          <el-table :data="stats?.namespaces || []" size="small" empty-text="暂无 Namespace 统计">
            <el-table-column prop="namespaceCode" label="Namespace" min-width="150" />
            <el-table-column prop="requestCount" label="请求数" width="110" sortable />
            <el-table-column prop="idCount" label="发号量" width="110" sortable />
            <el-table-column prop="avgQps" label="QPS" width="90" sortable />
            <el-table-column label="错误率" width="100">
              <template #default="{ row }">{{ row.errorRate }}%</template>
            </el-table-column>
            <el-table-column prop="instanceCount" label="实例数" width="90" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" v-loading="loading">
          <template #header><span class="fw-700">调用方 Top 10</span></template>
          <el-table :data="stats?.clients || []" size="small" empty-text="暂无调用方统计">
            <el-table-column prop="clientName" label="调用方" min-width="140" />
            <el-table-column prop="clientId" label="客户端 ID" min-width="140" />
            <el-table-column prop="requestCount" label="请求数" width="110" sortable />
            <el-table-column prop="idCount" label="发号量" width="110" sortable />
            <el-table-column prop="avgQps" label="QPS" width="90" sortable />
            <el-table-column label="错误率" width="100">
              <template #default="{ row }">{{ row.errorRate }}%</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { fetchCallStats } from '@/api/call-stats'
import { fetchNamespaces } from '@/api/namespace'
import type { CallStatsResponse } from '@/types/call-stats'
import type { NamespaceRecord } from '@/types/namespace'

const now = new Date()
const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000)

const loading = ref(false)
const timeRange = ref<[Date, Date]>([oneHourAgo, now])
const selectedNamespace = ref('')
const selectedInstance = ref('')
const clientName = ref('')
const namespaceOptions = ref<NamespaceRecord[]>([])
const stats = ref<CallStatsResponse | null>(null)

const summary = computed(() => stats.value?.summary)
const instanceOptions = computed(() => stats.value?.instances || [])
const trendPeak = computed(() => {
  const trend = stats.value?.trend || []
  return Math.max(0, ...trend.map((point) => point.totalRequests))
})

const overviewItems = computed(() => [
  {
    label: '总请求数',
    value: formatNumber(summary.value?.totalRequests),
    extra: `${formatNumber(summary.value?.totalIds)} 个 ID`,
  },
  {
    label: '成功请求',
    value: formatNumber(summary.value?.successRequests),
    extra: `${formatPercent(100 - (summary.value?.errorRate || 0))} 成功率`,
  },
  {
    label: '平均 QPS',
    value: formatNumber(summary.value?.avgQps),
    extra: `${formatNumber(summary.value?.failedRequests)} 次失败`,
  },
  {
    label: '平均延迟',
    value: `${formatNumber(summary.value?.avgLatencyMs)}ms`,
    extra: `最大 ${formatNumber(summary.value?.maxLatencyMs)}ms`,
  },
  {
    label: '在线实例',
    value: formatNumber(summary.value?.onlineInstanceCount),
    extra: `${formatLoadRate(summary.value?.avgLoadRate)} 平均负载`,
  },
  {
    label: '错误率',
    value: formatPercent(summary.value?.errorRate || 0),
    extra: '按请求数计算',
  },
])

const trendBars = computed(() => {
  const trend = stats.value?.trend || []
  const maxVisiblePoints = 72
  const step = Math.max(1, Math.ceil(trend.length / maxVisiblePoints))
  const visibleTrend = trend.filter((_, index) => index % step === 0)
  const maxRequests = Math.max(1, ...visibleTrend.map((point) => point.totalRequests))
  const labelStep = Math.max(1, Math.ceil(visibleTrend.length / 8))

  return visibleTrend.map((point, index) => {
    const totalRequests = point.totalRequests
    const successRatio = totalRequests > 0 ? (point.successRequests / totalRequests) * 100 : 0
    const failedRatio = totalRequests > 0 ? (point.failedRequests / totalRequests) * 100 : 0
    return {
      time: point.time,
      label: formatTime(point.time),
      fullLabel: formatDateTime(point.time),
      totalRequests,
      successRequests: point.successRequests,
      failedRequests: point.failedRequests,
      avgLatencyMs: point.avgLatencyMs,
      totalHeight: totalRequests > 0 ? Math.max(4, Math.round((totalRequests / maxRequests) * 100)) : 0,
      successRatio,
      failedRatio,
      showLabel: index % labelStep === 0 || index === visibleTrend.length - 1,
    }
  })
})

const handleQuery = async () => {
  loading.value = true
  try {
    const [startTime, endTime] = timeRange.value || []
    stats.value = await fetchCallStats({
      startTime: startTime?.toISOString(),
      endTime: endTime?.toISOString(),
      namespaceCode: selectedNamespace.value || undefined,
      instanceId: selectedInstance.value || undefined,
      clientName: clientName.value || undefined,
    })
  } finally {
    loading.value = false
  }
}

const loadNamespaces = async () => {
  const result = await fetchNamespaces({ pageNo: 1, pageSize: 200 })
  namespaceOptions.value = result.records
}

const handleExport = () => {
  if (!stats.value) return
  const rows = [
    ['类型', '名称', '请求数', '发号量', '平均QPS', '平均延迟(ms)', '最大延迟(ms)', '错误率(%)'],
    ...stats.value.namespaces.map((item) => [
      'Namespace',
      item.namespaceCode,
      item.requestCount,
      item.idCount,
      item.avgQps,
      item.avgLatencyMs,
      item.maxLatencyMs,
      item.errorRate,
    ]),
    ...stats.value.clients.map((item) => [
      '调用方',
      item.clientName,
      item.requestCount,
      item.idCount,
      item.avgQps,
      item.avgLatencyMs,
      item.maxLatencyMs,
      item.errorRate,
    ]),
  ]
  const csv = rows.map((row) => row.map(escapeCsv).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `serialx-call-stats-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('报表已导出')
}

const escapeCsv = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`

const formatNumber = (value?: number) => {
  if (value === undefined || value === null) return '0'
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)
}

const formatPercent = (value: number) => `${Math.max(0, value).toFixed(2)}%`

const formatLoadRate = (value?: number) => {
  if (value === undefined || value === null) return '-'
  return `${value.toFixed(2)}%`
}

const loadRateType = (value?: number) => {
  if (value === undefined || value === null) return 'info'
  if (value >= 90) return 'danger'
  if (value >= 70) return 'warning'
  return 'success'
}

const formatTime = (value: string) => {
  const date = new Date(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const formatDateTime = (value: string) => {
  const date = new Date(value)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${formatTime(value)}`
}

onMounted(async () => {
  await Promise.all([loadNamespaces(), handleQuery()])
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.filter-card,
.overview-row {
  margin-bottom: 20px;
}

.stat-mini-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 78px;
  padding: 16px 18px;
}

.stat-val {
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 700;
}

.stat-label,
.stat-extra {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.chart-box {
  min-height: 320px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot.success {
  background: var(--accent);
}

.legend-dot.failed {
  background: var(--red);
}

.legend-peak {
  padding-left: 4px;
  color: var(--text-muted);
}

.trend-chart {
  position: relative;
  height: 250px;
  padding: 14px 4px 0;
}

.trend-grid {
  position: absolute;
  inset: 14px 4px 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.grid-line {
  height: 1px;
  background: linear-gradient(to right, transparent, var(--border-light), transparent);
}

.trend-bars {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 100%;
}

.trend-bar-item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 7px;
  height: 100%;
}

.trend-bar-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 200px;
}

.trend-stack {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  width: min(16px, 70%);
  min-height: 2px;
  overflow: hidden;
  border-radius: 4px 4px 2px 2px;
  background: var(--bg-card-alt);
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.04);
  transition:
    height 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.trend-bar-item.active:hover .trend-stack {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
}

.trend-segment {
  width: 100%;
}

.trend-segment.success {
  background: linear-gradient(180deg, var(--accent), var(--accent-soft));
}

.trend-segment.failed {
  background: linear-gradient(180deg, var(--red), #fca5a5);
}

.trend-bar-label {
  height: 20px;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 20px;
}

.trend-bar-label.muted {
  color: transparent;
}

.trend-tooltip {
  position: absolute;
  bottom: 238px;
  left: 50%;
  z-index: 4;
  min-width: 148px;
  padding: 8px 10px;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.7;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.14);
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%) translateY(6px);
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.trend-bar-item:hover .trend-tooltip {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

.tooltip-time {
  margin-bottom: 2px;
  font-weight: 700;
}

.error-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 10px;
}

.error-main {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.error-code {
  color: var(--text-primary);
  font-weight: 600;
}

.error-count {
  color: var(--text-muted);
}

.mt-20 {
  margin-top: 20px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
  }

  .chart-header,
  .trend-legend {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .trend-bar-label {
    display: none;
  }
}
</style>
