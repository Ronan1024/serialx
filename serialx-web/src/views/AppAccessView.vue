<template>
  <div class="apps-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">应用接入</h1>
        <p class="page-subtitle">管理业务系统的身份凭证、Namespace 访问授权及资源配额</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">注册新应用</el-button>
      </div>
    </div>

    <!-- Summary -->
    <div class="app-summary-cards">
      <div class="summary-card">
        <div class="s-label">已注册应用</div>
        <div class="s-value">128</div>
      </div>
      <div class="summary-card">
        <div class="s-label">活跃应用 (24h)</div>
        <div class="s-value">96</div>
      </div>
      <div class="summary-card">
        <div class="s-label">密钥轮换提醒</div>
        <div class="s-value danger">3</div>
      </div>
    </div>

    <!-- Filter & Table -->
    <el-card shadow="never" class="main-card">
      <div class="table-toolbar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索应用名称 / AppKey / 负责人"
          :prefix-icon="Search"
          clearable
          style="width: 320px"
        />
        <div class="toolbar-right">
          <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px">
            <el-option label="已启用" value="active" />
            <el-option label="已禁用" value="disabled" />
          </el-select>
        </div>
      </div>

      <el-table :data="filteredApps" style="width: 100%">
        <el-table-column label="应用信息" min-width="200">
          <template #default="{ row }">
            <div class="app-info-cell">
              <div class="app-icon">{{ row.name[0] }}</div>
              <div class="app-meta">
                <div class="app-name fw-600">{{ row.name }}</div>
                <div class="app-id text-muted mono">{{ row.appKey }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="授权 Namespace" min-width="180">
          <template #default="{ row }">
            <div class="tag-group">
              <el-tag 
                v-for="ns in row.authorizedNamespaces.slice(0, 2)" 
                :key="ns" 
                size="small" 
                class="ns-tag"
              >
                {{ ns }}
              </el-tag>
              <el-tag v-if="row.authorizedNamespaces.length > 2" size="small" type="info">
                +{{ row.authorizedNamespaces.length - 2 }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="配额 (QPS)" width="120">
          <template #default="{ row }">
            <span class="quota-val">{{ row.quota }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="active"
              inactive-value="disabled"
              @change="(v: string) => handleStatusChange(row, v)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" @click="viewAppDetails(row)">管理</el-button>
              <el-button link type="primary" @click="manageAuth(row)">授权</el-button>
              <el-button link type="primary" @click="resetSecret(row)">重置密钥</el-button>
              <el-button link type="danger" @click="deleteApp(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create App Dialog -->
    <el-dialog v-model="createDialogVisible" title="注册新业务应用" width="540px">
      <el-form :model="appForm" :rules="appRules" label-position="top">
        <el-form-item label="应用名称" prop="name">
          <el-input v-model="appForm.name" placeholder="例如：OrderService-Production" />
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="appForm.owner" placeholder="负责人姓名/工号" />
        </el-form-item>
        <el-form-item label="初始配额 (QPS)" prop="quota">
          <el-input-number v-model="appForm.quota" :min="100" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="appForm.desc" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">立即注册</el-button>
      </template>
    </el-dialog>

    <!-- App Secrets / Details Drawer -->
    <el-drawer v-model="detailsVisible" :title="`应用详情: ${currentApp?.name}`" size="560px">
      <div v-if="currentApp" class="app-details">
        <div class="d-section">
          <h4 class="d-title">身份凭证</h4>
          <div class="secret-box">
            <div class="s-field">
              <label>App Key</label>
              <div class="s-input">
                <span class="mono">{{ currentApp.appKey }}</span>
                <el-button :icon="CopyDocument" link @click="copy(currentApp.appKey)" />
              </div>
            </div>
            <div class="s-field mt-16">
              <label>App Secret</label>
              <div class="s-input">
                <span v-if="!secretRevealed" class="mono">••••••••••••••••••••••••</span>
                <span v-else class="mono">sk_live_51P8M2YJp9Xv...</span>
                <el-button :icon="secretRevealed ? View : Hide" link @click="secretRevealed = !secretRevealed" />
                <el-button :icon="CopyDocument" link @click="copy('...') " v-if="secretRevealed" />
              </div>
            </div>
            <p class="s-tip">注意：App Secret 仅在创建时展示一次，泄露后请立即重置。</p>
          </div>
        </div>

        <div class="d-section mt-24">
          <h4 class="d-title">访问配额与策略</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="QPS 上限">{{ currentApp.quota }}</el-descriptions-item>
            <el-descriptions-item label="批量发号上限">1,000</el-descriptions-item>
            <el-descriptions-item label="鉴权方式">HmacSHA256 签名</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="d-section mt-24">
          <h4 class="d-title">最近调用审计</h4>
          <el-timeline>
            <el-timeline-item timestamp="2026-05-14 10:15:22" type="primary">
              成功获取 100 个 order_id
            </el-timeline-item>
            <el-timeline-item timestamp="2026-05-14 09:45:10" type="primary">
              成功获取 50 个 user_id
            </el-timeline-item>
            <el-timeline-item timestamp="2026-05-14 09:12:05" type="danger">
              调用失败: 签名校验不通过 (Invalid Signature)
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { Plus, Search, CopyDocument, View, Hide } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const searchQuery = ref('')
const statusFilter = ref('')
const createDialogVisible = ref(false)
const detailsVisible = ref(false)
const secretRevealed = ref(false)
const currentApp = ref<any>(null)

const apps = ref([
  { id: 1, name: '电商中台-订单中心', appKey: 'app_8219485723', owner: '张三', quota: 5000, status: 'active', authorizedNamespaces: ['order_id', 'payment_sn', 'ship_id'] },
  { id: 2, name: '用户中心-移动端', appKey: 'app_5120394851', owner: '李四', quota: 2000, status: 'active', authorizedNamespaces: ['user_id', 'profile_id'] },
  { id: 3, name: '风控系统-实时分析', appKey: 'app_1928374650', owner: '王五', quota: 10000, status: 'active', authorizedNamespaces: ['trace_id', 'risk_event_id'] },
  { id: 4, name: '测试应用-内部工具', appKey: 'app_test_999999', owner: '赵六', quota: 500, status: 'disabled', authorizedNamespaces: ['test_id'] },
])

const filteredApps = computed(() => {
  return apps.value.filter(app => {
    const matchSearch = !searchQuery.value || 
      app.name.toLowerCase().includes(searchQuery.value.toLowerCase()) || 
      app.appKey.includes(searchQuery.value)
    const matchStatus = !statusFilter.value || app.status === statusFilter.value
    return matchSearch && matchStatus
  })
})

const appForm = reactive({
  name: '',
  owner: '',
  quota: 1000,
  desc: ''
})

const appRules = {
  name: [{ required: true, message: '请输入应用名称' }],
  owner: [{ required: true, message: '请输入负责人' }],
}

const openCreateDialog = () => {
  Object.assign(appForm, { name: '', owner: '', quota: 1000, desc: '' })
  createDialogVisible.value = true
}

const handleCreate = () => {
  ElMessage.success('应用注册成功，请保存密钥')
  createDialogVisible.value = false
}

const viewAppDetails = (app: any) => {
  currentApp.value = app
  secretRevealed.value = false
  detailsVisible.value = true
}

const handleStatusChange = (app: any, status: string) => {
  ElMessage.success(`应用已${status === 'active' ? '启用' : '禁用'}`)
}

const manageAuth = (app: any) => {
  ElMessage.info(`正在管理应用 ${app.name} 的授权`)
}

const resetSecret = (app: any) => {
  ElMessageBox.confirm(`重置密钥后，原 App Secret 将立即失效，相关业务调用将中断。确定重置吗？`, '危险操作警告', {
    confirmButtonText: '确定重载',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    ElMessage.success('密钥已重置')
  })
}

const deleteApp = (app: any) => {
  ElMessageBox.confirm(`确定删除应用 ${app.name} 吗？`, '删除确认', { type: 'warning' })
    .then(() => {
      ElMessage.success('应用已删除')
    })
}

const copy = (text: string) => {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制到剪贴板')
}
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

.app-summary-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.summary-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
}

.s-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.s-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--accent);
}
.s-value.danger { color: var(--red); }

.table-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}

.app-info-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-icon {
  width: 36px;
  height: 36px;
  background: var(--bg-card-alt);
  color: var(--accent);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
}

.app-name {
  font-size: 14px;
  color: var(--text-primary);
}

.app-id {
  font-size: 12px;
  margin-top: 2px;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ns-tag {
  background: var(--bg-card-alt);
  border: none;
  color: var(--text-secondary);
}

.quota-val {
  font-weight: 700;
  color: var(--text-primary);
}

/* Drawer Details */
.app-details {
  padding: 0 20px;
}

.d-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.secret-box {
  background: var(--bg-card-alt);
  padding: 20px;
  border-radius: 12px;
}

.s-field label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 8px;
}

.s-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
}

.s-tip {
  font-size: 11px;
  color: var(--amber);
  margin-top: 12px;
  font-weight: 500;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
