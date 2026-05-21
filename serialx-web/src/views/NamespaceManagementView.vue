<template>
  <div class="namespace-management">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">Namespace 治理</h1>
        <p class="page-subtitle">定义发号规则、号段策略及 Snowflake 节点配置，实时推送生效</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建 Namespace</el-button>
      </div>
    </div>

    <!-- Stats row -->
    <el-row :gutter="20" class="summary-row">
      <el-col :span="6" v-for="stat in nsStats" :key="stat.label">
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

    <!-- Filter Card -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="查找">
          <el-input
            v-model="queryForm.keyword"
            placeholder="编码 / 名称 / 负责人"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="草稿 (DRAFT)" :value="0" />
            <el-option label="已启用 (ENABLED)" :value="1" />
            <el-option label="已停用 (DISABLED)" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="模式">
          <el-select v-model="queryForm.idMode" placeholder="发号模式" clearable style="width: 130px">
            <el-option label="SEGMENT" :value="1" />
            <el-option label="SNOWFLAKE" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table Card -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="tableLoading" :data="tableData" row-key="id">
        <el-table-column label="Namespace 资源" min-width="240">
          <template #default="{ row }">
            <div class="ns-cell">
              <div class="ns-icon-wrap" :class="row.idMode === 1 ? 'segment' : 'snowflake'">
                <el-icon><component :is="row.idMode === 1 ? 'Grid' : 'Opportunity'" /></el-icon>
              </div>
              <div class="ns-meta">
                <div class="ns-code mono">{{ row.namespaceCode }}</div>
                <div class="ns-name">{{ row.namespaceName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="配置模式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.idMode === 1 ? 'primary' : 'warning'" effect="plain" size="small">
              {{ row.idMode === 1 ? 'SEGMENT' : 'SNOWFLAKE' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="运行状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="生效版本" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.currentVersion" type="info" size="small" class="mono">v{{ row.currentVersion }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="owner" label="负责人" width="120" />
        <el-table-column prop="updatedAt" label="最后更新" width="160" />
        
        <el-table-column label="管理操作" min-width="360" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" @click="openConfigDialog(row.id)">配置</el-button>
              <el-button link type="primary" @click="openTestDialog(row)">测试</el-button>
              <el-button link type="success" @click="handlePublish(row.id)">发布</el-button>
              
              <el-divider direction="vertical" />
              
              <el-button v-if="row.status !== 1" link type="success" @click="handleEnable(row.id)">启用</el-button>
              <el-button v-if="row.status === 1" link type="warning" @click="handleDisable(row.id)">停用</el-button>
              
              <el-dropdown trigger="click">
                <el-button link type="primary" class="more-btn">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openRollbackDialog(row)" :icon="RefreshLeft">版本回滚</el-dropdown-item>
                    <el-dropdown-item @click="openLogsDrawer(row)" :icon="List">操作日志</el-dropdown-item>
                    <el-dropdown-item @click="openInstancesDrawer(row)" :icon="Monitor">实例同步</el-dropdown-item>
                    <el-dropdown-item divided @click="handleDelete(row.id)" :icon="Delete" style="color: var(--red)">删除资源</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          @size-change="loadNamespaces"
          @current-change="loadNamespaces"
        />
      </div>
    </el-card>

    <!-- Test ID Dialog -->
    <el-dialog v-model="testVisible" :title="`发号测试: ${testNs?.namespaceCode}`" width="480px">
      <div class="test-container">
        <div class="test-controls">
          <el-input-number v-model="testCount" :min="1" :max="100" label="获取数量" />
          <el-button type="primary" :loading="testing" @click="doTestGenerate">立即发号</el-button>
        </div>
        <div class="test-result" v-if="testResultIds.length > 0">
          <div class="result-title">生成结果 ({{ testResultIds.length }}):</div>
          <div class="result-list mono">
            <div v-for="id in testResultIds" :key="id" class="result-id">{{ id }}</div>
          </div>
        </div>
        <el-alert v-if="testNs?.status !== 1" title="Namespace 未启用，测试发号可能失败" type="warning" show-icon />
      </div>
    </el-dialog>

    <!-- Rollback Dialog (Version Selection) -->
    <el-dialog v-model="rollbackVisible" title="选择历史版本回滚" width="640px">
      <el-table :data="historyVersions" size="small" v-loading="historyLoading">
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">
            <span class="mono fw-700">v{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">
            {{ row.publishedAt || row.createdAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="发布人" width="100" />
        <el-table-column label="配置摘要">
          <template #default="{ row }">
            <span>{{ row.idModeName || (row.idMode === 1 ? 'SEGMENT' : 'SNOWFLAKE') }}</span>
            <span v-if="row.configChecksum" class="text-muted mono"> / {{ row.configChecksum }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="doRollback(row.version)">回滚至此</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Drawer: Diff View in Logs (Integrated into existing Log Drawer logic) -->
    <el-drawer v-model="logsDrawerVisible" title="变更历史与差异" size="700px">
      <div v-for="log in logRecords" :key="log.id" class="log-item-v2">
        <div class="log-v2-header">
           <el-tag size="small" :type="logActionType(log.actionType)">{{ log.actionTypeName }}</el-tag>
           <span class="log-v2-ver mono">v{{ log.version }}</span>
           <span class="log-v2-time">{{ log.createdAt }}</span>
        </div>
        <div class="log-v2-op">由 {{ log.operator }} 变更</div>
        <div class="log-v2-diff">
          <div class="diff-chip" @click="toggleLogJson(log.id)">
            <el-icon><component :is="expandedLogs[log.id] ? 'CaretBottom' : 'CaretRight'" /></el-icon>
            查看配置详情
          </div>
          <el-collapse-transition>
            <div v-show="expandedLogs[log.id]" class="diff-json-box">
              <pre class="mono">{{ log.afterJson }}</pre>
            </div>
          </el-collapse-transition>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="instancesDrawerVisible" title="发号服务同步状态" size="760px">
      <el-table :data="instanceRecords" size="small" v-loading="instancesLoading">
        <el-table-column prop="instanceId" label="实例 ID" min-width="180" />
        <el-table-column prop="host" label="主机" width="140" />
        <el-table-column prop="loadedVersion" label="加载版本" width="100">
          <template #default="{ row }">
            <span class="mono">v{{ row.loadedVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="runtimeStatus" label="运行态" width="120" />
        <el-table-column prop="lastReportTime" label="上报时间" width="170" />
        <el-table-column prop="lastOfflineTime" label="最近离线时间" width="170" />
      </el-table>
    </el-drawer>

    <!-- Create Namespace Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建 Namespace" width="600px">
      <el-form :model="createForm" label-width="120px" :rules="createRules" ref="createFormRef">
        <el-form-item label="Namespace 编码" prop="namespaceCode">
          <el-input v-model="createForm.namespaceCode" placeholder="唯一标识，创建后不可修改" />
        </el-form-item>
        <el-form-item label="Namespace 名称" prop="namespaceName">
          <el-input v-model="createForm.namespaceName" placeholder="业务可读名称" />
        </el-form-item>
        <el-form-item label="发号模式" prop="idMode">
          <el-select v-model="createForm.idMode" style="width: 100%">
            <el-option label="SEGMENT" :value="1" />
            <el-option label="SNOWFLAKE" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="createForm.owner" placeholder="负责人" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
        
        <div v-if="createForm.idMode === 1">
           <el-divider content-position="left">Segment 初始配置</el-divider>
           <el-form-item label="号段步长">
             <el-input-number v-model="createForm.stepSize" :min="1" />
           </el-form-item>
           <el-form-item label="最大上限">
             <el-input-number v-model="createForm.maxValue" :min="1" style="width: 180px" />
           </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreateForm">确认新建</el-button>
      </template>
    </el-dialog>

    <!-- Other Dialogs (Simplified) -->
    <el-dialog v-model="configDialogVisible" title="发号参数配置 (草稿)" width="600px">
       <el-form :model="configForm" label-width="120px">
         <div v-if="configForm.idMode === 1">
           <el-form-item label="号段步长">
             <el-input-number v-model="configForm.stepSize" :min="1" />
           </el-form-item>
           <el-form-item label="最大上限">
             <el-input-number v-model="configForm.maxValue" :min="1" style="width: 180px" />
           </el-form-item>
         </div>
         <div v-else>
           <el-empty description="Snowflake 模式无需额外参数" />
         </div>
       </el-form>
       <template #footer>
         <el-button @click="configDialogVisible = false">取消</el-button>
         <el-button type="primary" @click="submitConfigForm">保存草稿</el-button>
       </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  Plus, Grid, Opportunity, Delete, List, Monitor,
  RefreshLeft, CaretRight, CaretBottom,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchNamespaces, fetchNamespaceDetail, publishNamespace,
  enableNamespace, disableNamespace, deleteNamespace,
  fetchNamespaceChangeLogs, rollbackNamespace, createNamespace,
  saveNamespaceDraftConfig, fetchNamespaceVersions, fetchNamespaceInstances,
} from '@/api/namespace'
import { batchGenerateIds, generateId } from '@/api/id'
import type {
  NamespaceInstanceStatusRecord,
  NamespaceRecord,
  NamespaceVersionRecord,
} from '@/types/namespace'

const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<any>(null)
const createForm = reactive({
  namespaceCode: '',
  namespaceName: '',
  idMode: 1,
  owner: '',
  remark: '',
  stepSize: 1000,
  maxValue: 1000000000
})
const createRules = {
  namespaceCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  namespaceName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  owner: [{ required: true, message: '请输入负责人', trigger: 'blur' }]
}

const openCreateDialog = () => {
  Object.assign(createForm, {
    namespaceCode: '', namespaceName: '', idMode: 1, owner: '', remark: '', stepSize: 1000, maxValue: 1000000000
  })
  createDialogVisible.value = true
}

const submitCreateForm = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  creating.value = true
  try {
    const payload = {
      namespaceCode: createForm.namespaceCode,
      namespaceName: createForm.namespaceName,
      idMode: createForm.idMode,
      owner: createForm.owner,
      remark: createForm.remark,
      config: createForm.idMode === 1 ? {
        stepSize: createForm.stepSize,
        maxValue: createForm.maxValue,
        initialValue: 1,
        cacheSegments: 1,
      } : {}
    }
    await createNamespace(payload)
    ElMessage.success('Namespace 创建成功')
    createDialogVisible.value = false
    loadNamespaces()
  } catch (error) {
    console.error('Create namespace failed:', error)
  } finally {
    creating.value = false
  }
}

const tableLoading = ref(false)
const tableData = ref<any[]>([])
const pagination = reactive({ total: 0 })
const queryForm = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: undefined, idMode: undefined })

const nsStats = [
  { label: 'Namespace 总量', value: '42', icon: Grid, type: 'primary' },
  { label: '活跃模式', value: 'SEGMENT', icon: Opportunity, type: 'success' },
  { label: '今日生成 ID', value: '2.4M', icon: List, type: 'warning' },
  { label: '同步异常', value: '0', icon: Monitor, type: 'danger' },
]

const loadNamespaces = async () => {
  tableLoading.value = true
  try {
    const res = await fetchNamespaces(queryForm)
    tableData.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('Load namespaces failed:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

onMounted(loadNamespaces)

const handleSearch = () => { loadNamespaces() }
const handleReset = () => { Object.assign(queryForm, { keyword: '', status: undefined, idMode: undefined }); loadNamespaces() }

// Test Dialog
const testVisible = ref(false)
const testNs = ref<NamespaceRecord | null>(null)
const testing = ref(false)
const testCount = ref(1)
const testResultIds = ref<string[]>([])

const openTestDialog = (row: NamespaceRecord) => {
  testNs.value = row
  testResultIds.value = []
  testVisible.value = true
}

const doTestGenerate = async () => {
  if (!testNs.value) return
  testing.value = true
  try {
    if (testCount.value === 1) {
      const response = await generateId({ namespaceCode: testNs.value.namespaceCode })
      testResultIds.value = [response.id]
    } else {
      const response = await batchGenerateIds({
        namespaceCode: testNs.value.namespaceCode,
        count: testCount.value,
      })
      testResultIds.value = response.ids
    }
    ElMessage.success('发号成功')
  } finally {
    testing.value = false
  }
}

// Rollback Dialog
const rollbackVisible = ref(false)
const historyLoading = ref(false)
const historyVersions = ref<NamespaceVersionRecord[]>([])
const currentRollbackNs = ref<NamespaceRecord | null>(null)

const openRollbackDialog = async (row: NamespaceRecord) => {
  currentRollbackNs.value = row
  rollbackVisible.value = true
  historyLoading.value = true
  try {
    historyVersions.value = await fetchNamespaceVersions(row.id)
  } finally {
    historyLoading.value = false
  }
}

const doRollback = (ver: number) => {
  if (!currentRollbackNs.value) return
  ElMessageBox.confirm(`确认要将 ${currentRollbackNs.value.namespaceCode} 回滚至版本 v${ver} 吗？`, '回滚确认')
    .then(async () => {
      await rollbackNamespace(currentRollbackNs.value!.id, ver)
      ElMessage.success('回滚指令已下发')
      rollbackVisible.value = false
      loadNamespaces()
    })
}

// Log Drawer with Expansion
const logsDrawerVisible = ref(false)
const logRecords = ref<any[]>([])
const expandedLogs = reactive<Record<number, boolean>>({})

const openLogsDrawer = async (row: any) => {
  logRecords.value = await fetchNamespaceChangeLogs(row.id)
  logsDrawerVisible.value = true
}

const toggleLogJson = (id: number) => {
  expandedLogs[id] = !expandedLogs[id]
}

const instancesDrawerVisible = ref(false)
const instancesLoading = ref(false)
const instanceRecords = ref<NamespaceInstanceStatusRecord[]>([])

const openInstancesDrawer = async (row: NamespaceRecord) => {
  instancesDrawerVisible.value = true
  instancesLoading.value = true
  try {
    instanceRecords.value = await fetchNamespaceInstances(row.id)
  } finally {
    instancesLoading.value = false
  }
}

// Config Dialog (Simplified)
const configDialogVisible = ref(false)
const configForm = reactive({ id: 0, idMode: 1, stepSize: 1000, maxValue: 1000000 })

const openConfigDialog = async (id: number) => {
  const data = await fetchNamespaceDetail(id)
  const config = data.draftConfig || data.publishedConfig || {}
  Object.assign(configForm, { id: data.id, idMode: data.idMode, ...config })
  configDialogVisible.value = true
}

const submitConfigForm = async () => {
  await saveNamespaceDraftConfig(configForm.id, {
    idMode: configForm.idMode,
    config: configForm.idMode === 1 ? {
      stepSize: configForm.stepSize,
      maxValue: configForm.maxValue,
      initialValue: 1,
      cacheSegments: 1,
    } : {},
  })
  ElMessage.success('配置草稿已保存')
  configDialogVisible.value = false
  loadNamespaces()
}

// Handlers
const handlePublish = async (id: number) => {
  await ElMessageBox.confirm('发布后将更新生效版本并推送发号服务刷新，确定继续吗？', '发布确认')
  await publishNamespace(id)
  ElMessage.success('已发布并推送生效')
  loadNamespaces()
}

const handleEnable = async (id: number) => { await enableNamespace(id); ElMessage.success('已启用'); loadNamespaces() }
const handleDisable = async (id: number) => { await disableNamespace(id); ElMessage.warning('已停用'); loadNamespaces() }
const handleDelete = async (id: number) => {
  await ElMessageBox.confirm('确定彻底删除该 Namespace 吗？', '危险操作', { type: 'error' })
  await deleteNamespace(id)
  ElMessage.success('已删除')
  loadNamespaces()
}

// Helpers
const statusTagType = (s: number) => s === 1 ? 'success' : s === 2 ? 'danger' : 'info'
const statusLabel = (s: number) => ['DRAFT', 'ENABLED', 'DISABLED'][s] || 'UNKNOWN'
const logActionType = (a: number) => a === 1 ? 'success' : 'primary'
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.summary-row {
  margin-bottom: 20px;
}

.stat-mini-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}

.stat-val {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.stat-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.stat-icon-box.primary { background: var(--accent-soft); color: var(--accent); }
.stat-icon-box.success { background: rgba(16, 185, 129, 0.1); color: var(--green); }
.stat-icon-box.warning { background: rgba(245, 158, 11, 0.1); color: var(--amber); }
.stat-icon-box.danger  { background: rgba(239, 68, 68, 0.1); color: var(--red); }

.filter-card {
  margin-bottom: 20px;
}

.filter-form {
  margin-bottom: -18px;
}

.ns-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ns-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.ns-icon-wrap.segment { background: var(--accent-soft); color: var(--accent); }
.ns-icon-wrap.snowflake { background: rgba(245, 158, 11, 0.1); color: var(--amber); }

.ns-code {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
}

.ns-name {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.table-footer {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
}

/* Test Dialog */
.test-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.test-controls {
  display: flex;
  gap: 12px;
}

.test-result {
  background: var(--bg-card-alt);
  border-radius: 12px;
  padding: 16px;
}

.result-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.result-list {
  max-height: 200px;
  overflow-y: auto;
  font-size: 13px;
  color: var(--accent);
  line-height: 1.8;
}

/* Log Drawer */
.log-item-v2 {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
}

.log-v2-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.log-v2-ver {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.log-v2-time {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-muted);
}

.log-v2-op {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.diff-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  cursor: pointer;
  padding: 4px 8px;
  background: var(--accent-soft);
  border-radius: 4px;
}

.diff-json-box {
  margin-top: 12px;
  background: #0f172a;
  color: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  overflow-x: auto;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}
.more-btn {
  margin-left: 12px;
}
</style>
