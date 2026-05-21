<template>
  <div class="nodes-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">节点管理</h1>
        <p class="page-subtitle">查看发号服务实例心跳、Namespace 加载版本和同步状态</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">刷新状态</el-button>
      </div>
    </div>

    <!-- Cluster Overview -->
    <el-row :gutter="20" class="overview-row">
      <el-col :span="6" v-for="stat in nodeStats" :key="stat.label">
        <el-card shadow="never" class="stat-mini-card">
          <div class="mini-stat-content">
            <div class="stat-val">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
          <div class="stat-icon-box" :class="stat.type">
            <el-icon><component :is="stat.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Node Table -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-header-box">
          <span class="fw-700">活跃节点列表</span>
          <el-radio-group v-model="filterStatus" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="ACTIVE">在线</el-radio-button>
            <el-radio-button label="OFFLINE">离线</el-radio-button>
            <el-radio-button label="lagging">版本落后</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="filteredNodes" style="width: 100%" v-loading="loading">
        <el-table-column label="节点 ID / 主机" min-width="260">
          <template #default="{ row }">
            <div class="node-main">
              <div class="node-id mono">{{ row.instanceId }}</div>
              <div class="node-host">{{ row.serviceName }} · {{ row.host }}</div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="运行状态" width="120">
          <template #default="{ row }">
            <el-tag :type="isOnline(row.runtimeStatus) ? 'success' : 'info'" effect="dark">
              {{ row.runtimeStatus }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Namespace" width="160">
          <template #default="{ row }">
            <div class="lease-tag">
              <span class="l-label">已加载:</span>
              <span class="l-val mono">{{ row.namespaceCount }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="版本同步" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.laggingNamespaceCount > 0" type="warning">
              {{ row.laggingNamespaceCount }} 个落后
            </el-tag>
            <el-tag v-else type="success">已同步</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="最后心跳" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastReportTime) }}
          </template>
        </el-table-column>

        <el-table-column label="最近离线" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastOfflineTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" @click="viewDetails(row)">详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Node Details Drawer -->
    <el-drawer
      v-model="detailsVisible"
      :title="`节点详情: ${currentNode?.instanceId}`"
      size="600px"
      destroy-on-close
    >
      <div v-if="currentNode" class="details-content">
        <div class="detail-section">
          <h3 class="section-title">基础属性</h3>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="服务名称">{{ currentNode.serviceName }}</el-descriptions-item>
            <el-descriptions-item label="主机标识">{{ currentNode.host }}</el-descriptions-item>
            <el-descriptions-item label="运行状态">{{ currentNode.runtimeStatus }}</el-descriptions-item>
            <el-descriptions-item label="最后心跳">{{ formatDate(currentNode.lastReportTime) }}</el-descriptions-item>
            <el-descriptions-item label="最近离线">{{ formatDate(currentNode.lastOfflineTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section mt-16">
          <h3 class="section-title">已加载 Namespace ({{ currentNode.namespaceCount }})</h3>
          <el-table :data="currentNode.namespaces" size="small" border>
            <el-table-column prop="namespaceCode" label="编码" min-width="130" />
            <el-table-column prop="namespaceName" label="名称" min-width="120" />
            <el-table-column label="加载版本" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">v{{ row.loadedVersion ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前版本" width="100">
              <template #default="{ row }">
                <el-tag size="small">v{{ row.currentVersion ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span class="status-dot" :class="{ online: isOnline(row.runtimeStatus) }"></span>
                {{ row.runtimeStatus }}
              </template>
            </el-table-column>
            <el-table-column label="同步" width="100">
              <template #default="{ row }">
                <el-tag :type="row.lagging ? 'warning' : 'success'" size="small">
                  {{ row.lagging ? '落后' : '同步' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh, Monitor, Cpu, Connection, Timer } from '@element-plus/icons-vue'
import { fetchServiceInstanceNodes } from '@/api/service-instance'
import type {
  ServiceInstanceNode,
  ServiceInstanceNodeSummary,
} from '@/types/service-instance'

const filterStatus = ref('all')
const detailsVisible = ref(false)
const loading = ref(false)
const currentNode = ref<ServiceInstanceNode | null>(null)
const nodes = ref<ServiceInstanceNode[]>([])
const summary = ref<ServiceInstanceNodeSummary>({
  totalCount: 0,
  onlineCount: 0,
  offlineCount: 0,
  laggingNamespaceCount: 0,
})

const nodeStats = computed(() => [
  { label: '总节点数', value: String(summary.value.totalCount), icon: Monitor, type: 'primary' },
  { label: '在线节点', value: String(summary.value.onlineCount), icon: Cpu, type: 'success' },
  { label: '版本落后', value: String(summary.value.laggingNamespaceCount), icon: Connection, type: 'warning' },
  { label: '离线节点', value: String(summary.value.offlineCount), icon: Timer, type: 'danger' },
])

const filteredNodes = computed(() => {
  if (filterStatus.value === 'all') return nodes.value
  if (filterStatus.value === 'lagging') {
    return nodes.value.filter((node) => node.laggingNamespaceCount > 0)
  }
  return nodes.value.filter((node) => node.runtimeStatus === filterStatus.value)
})

const handleRefresh = async () => {
  loading.value = true
  try {
    const result = await fetchServiceInstanceNodes()
    summary.value = result.summary
    nodes.value = result.nodes
  } finally {
    loading.value = false
  }
}

const viewDetails = (node: ServiceInstanceNode) => {
  currentNode.value = node
  detailsVisible.value = true
}

const isOnline = (runtimeStatus?: string) => runtimeStatus === 'ACTIVE'

const formatDate = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(handleRefresh)
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

.overview-row {
  margin-bottom: 24px;
}

.stat-mini-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}

.stat-val {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.stat-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.stat-icon-box.primary { background: var(--accent-soft); color: var(--accent); }
.stat-icon-box.success { background: rgba(16, 185, 129, 0.1); color: var(--green); }
.stat-icon-box.warning { background: rgba(245, 158, 11, 0.1); color: var(--amber); }
.stat-icon-box.danger  { background: rgba(239, 68, 68, 0.1); color: var(--red); }

.table-header-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-main {
  display: flex;
  flex-direction: column;
}

.node-id {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.node-host {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.lease-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  background: var(--bg-card-alt);
  border-radius: 6px;
  font-size: 12px;
}

.l-label { color: var(--text-muted); }
.l-val { color: var(--accent); font-weight: 700; }

.load-text {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* Drawer Details */
.detail-section {
  padding-bottom: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--text-primary);
  border-left: 3px solid var(--accent);
  padding-left: 8px;
}

.monitor-item {
  background: var(--bg-card-alt);
  padding: 16px;
  border-radius: 12px;
  text-align: center;
}

.m-val {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}

.m-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  background: var(--slate);
}
.status-dot.online { background: var(--green); }

.action-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}
.more-btn {
  margin-left: 12px;
}
</style>
