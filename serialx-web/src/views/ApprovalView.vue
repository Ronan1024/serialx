<template>
  <div class="approvals-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">审批中心</h1>
        <p class="page-subtitle">审核 Namespace 配置发布、灰度上线及高危回滚申请</p>
      </div>
    </div>

    <!-- Tabs for different states -->
    <el-tabs v-model="activeTab" class="approval-tabs">
      <el-tab-pane label="待我处理" name="pending">
        <template #label>
          <div class="tab-label">
            待我处理
            <span class="tab-count" v-if="pendingApprovals.length">{{ pendingApprovals.length }}</span>
          </div>
        </template>
        
        <div v-if="pendingApprovals.length === 0" class="empty-state">
          <el-empty description="暂无待审批申请" />
        </div>
        
        <el-card v-for="item in pendingApprovals" :key="item.id" shadow="never" class="approval-item-card">
          <div class="approval-item-main">
            <div class="type-icon" :class="item.type">
              <el-icon><component :is="typeIcon(item.type)" /></el-icon>
            </div>
            <div class="item-info">
              <div class="item-title">
                <span class="fw-700">{{ item.namespaceCode }}</span>
                <el-tag size="small" :type="item.type === 'publish' ? 'success' : 'warning'" class="ml-8">
                  {{ item.typeName }}
                </el-tag>
              </div>
              <div class="item-meta">
                <span>申请人: {{ item.applicant }}</span>
                <el-divider direction="vertical" />
                <span>提交时间: {{ item.createdAt }}</span>
              </div>
              <div class="item-reason">理由: {{ item.reason }}</div>
            </div>
            <div class="item-actions">
              <el-button @click="viewDiff(item)">查看差异</el-button>
              <el-button type="danger" plain @click="handleReject(item)">驳回</el-button>
              <el-button type="primary" @click="handleApprove(item)">批准并执行</el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
      
      <el-tab-pane label="我发起的" name="my-requests">
        <el-card v-for="item in myRequests" :key="item.id" shadow="never" class="approval-item-card">
          <div class="approval-item-main">
            <div class="type-icon" :class="item.type">
              <el-icon><component :is="typeIcon(item.type)" /></el-icon>
            </div>
            <div class="item-info">
              <div class="item-title">
                <span class="fw-700">{{ item.namespaceCode }}</span>
                <el-tag size="small" :type="statusTag(item.status)" class="ml-8">{{ item.statusName }}</el-tag>
              </div>
              <div class="item-meta">
                <span>类型: {{ item.typeName }}</span>
                <el-divider direction="vertical" />
                <span>更新时间: {{ item.updatedAt }}</span>
              </div>
            </div>
            <div class="item-actions">
              <el-button link type="primary" @click="viewDiff(item)">详情</el-button>
              <el-button v-if="item.status === 'pending'" link type="danger">撤销申请</el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
      
      <el-tab-pane label="已结办" name="closed">
        <el-table :data="closedApprovals" border style="width: 100%">
          <el-table-column prop="namespaceCode" label="Namespace" width="150" />
          <el-table-column prop="typeName" label="申请类型" width="120" />
          <el-table-column prop="applicant" label="申请人" width="100" />
          <el-table-column prop="approver" label="审批人" width="100" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="row.result === 'approved' ? 'success' : 'danger'" size="small">
                {{ row.resultName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="closedAt" label="办结时间" width="180" />
          <el-table-column prop="remark" label="审批意见" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Diff Dialog -->
    <el-dialog v-model="diffVisible" title="版本变更差异对比 (Diff)" width="800px">
      <div v-if="currentDiff" class="diff-viewer">
        <div class="diff-header">
          <div class="diff-col">
            <div class="diff-label">当前运行版本 (v{{ currentDiff.oldVersion }})</div>
          </div>
          <div class="diff-col">
            <div class="diff-label">待发布版本 (v{{ currentDiff.newVersion }})</div>
          </div>
        </div>
        <div class="diff-body">
           <div class="diff-row" v-for="(line, idx) in currentDiff.lines" :key="idx" :class="line.type">
             <div class="line-num">{{ Number(idx) + 1 }}</div>
             <div class="line-content">{{ line.content }}</div>
           </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="diffVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Upload, RefreshLeft, Grid, Opportunity } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('pending')
const diffVisible = ref(false)
const currentDiff = ref<any>(null)

const pendingApprovals = ref([
  { 
    id: 1, 
    namespaceCode: 'order_id', 
    type: 'publish', 
    typeName: '新版本发布', 
    applicant: 'Ronan', 
    createdAt: '2026-05-14 11:20:15', 
    reason: '业务高峰期扩容，调整 Segment 步长从 1000 到 5000' 
  },
  { 
    id: 2, 
    namespaceCode: 'user_profile_sn', 
    type: 'rollback', 
    typeName: '版本回滚', 
    applicant: 'Jiang', 
    createdAt: '2026-05-14 09:10:05', 
    reason: '上个版本配置错误导致部分请求超时，需紧急回滚至 v14' 
  }
])

const myRequests = ref([
  { id: 3, namespaceCode: 'payment_flow', type: 'publish', typeName: '新版本发布', status: 'pending', statusName: '待审批', updatedAt: '2026-05-14 10:00:00' },
  { id: 4, namespaceCode: 'inventory_id', type: 'publish', typeName: '新版本发布', status: 'rejected', statusName: '已驳回', updatedAt: '2026-05-13 16:30:00' },
])

const closedApprovals = ref([
  { namespaceCode: 'shipping_id', typeName: '配置发布', applicant: 'Jiang', approver: 'admin', result: 'approved', resultName: '已通过', closedAt: '2026-05-14 08:30:00', remark: '通过，准予发布' },
  { namespaceCode: 'test_ns', typeName: '删除申请', applicant: 'Ronan', approver: 'admin', result: 'rejected', resultName: '已拒绝', closedAt: '2026-05-13 15:45:00', remark: '该 Namespace 尚有业务调用，禁止删除' },
])

const typeIcon = (type: string) => type === 'publish' ? Upload : RefreshLeft
const statusTag = (status: string) => status === 'pending' ? 'warning' : status === 'rejected' ? 'danger' : 'success'

const viewDiff = (item: any) => {
  currentDiff.value = {
    oldVersion: 12,
    newVersion: 13,
    lines: [
      { type: 'normal', content: '{' },
      { type: 'normal', content: '  "namespace": "order_id",' },
      { type: 'deleted', content: '  "stepSize": 1000,' },
      { type: 'added', content: '  "stepSize": 5000,' },
      { type: 'normal', content: '  "maxValue": 99999999' },
      { type: 'normal', content: '}' }
    ]
  }
  diffVisible.value = true
}

const handleApprove = (item: any) => {
  ElMessageBox.confirm(`确定要批准并执行 ${item.namespaceCode} 的 ${item.typeName} 申请吗？`, '审批确认', {
    type: 'success'
  }).then(() => {
    ElMessage.success('申请已批准，正在下发指令')
    pendingApprovals.value = pendingApprovals.value.filter(a => a.id !== item.id)
  })
}

const handleReject = (item: any) => {
  ElMessageBox.prompt('请输入驳回原因', '审批驳回', {
    confirmButtonText: '确定驳回',
    cancelButtonText: '取消',
    inputPattern: /\S/,
    inputErrorMessage: '原因不能为空'
  }).then(({ value }) => {
    ElMessage.warning(`申请已驳回: ${value}`)
    pendingApprovals.value = pendingApprovals.value.filter(a => a.id !== item.id)
  })
}
</script>

<style scoped>
.page-header {
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

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab-count {
  background: var(--red);
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 800;
}

.approval-item-card {
  margin-bottom: 16px;
}

.approval-item-main {
  display: flex;
  align-items: center;
  gap: 20px;
}

.type-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.type-icon.publish { background: var(--accent-soft); color: var(--accent); }
.type-icon.rollback { background: rgba(245, 158, 11, 0.1); color: var(--amber); }

.item-info {
  flex: 1;
}

.item-title {
  font-size: 16px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
}

.item-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.item-reason {
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-page);
  padding: 8px 12px;
  border-radius: 6px;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ml-8 { margin-left: 8px; }

/* Diff Viewer */
.diff-viewer {
  background: #0f172a;
  border-radius: 8px;
  overflow: hidden;
  font-family: 'JetBrains Mono', monospace;
  color: #f8fafc;
}

.diff-header {
  display: flex;
  background: #1e293b;
  border-bottom: 1px solid #334155;
}

.diff-col {
  flex: 1;
  padding: 12px 16px;
}

.diff-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
}

.diff-body {
  padding: 12px 0;
}

.diff-row {
  display: flex;
  font-size: 13px;
  line-height: 1.6;
}
.diff-row.added { background: rgba(16, 185, 129, 0.2); }
.diff-row.deleted { background: rgba(239, 68, 68, 0.2); }

.line-num {
  width: 40px;
  text-align: right;
  padding-right: 12px;
  color: #475569;
  user-select: none;
}

.line-content {
  flex: 1;
  padding-left: 8px;
  white-space: pre-wrap;
}

.diff-row.added .line-content::before { content: '+ '; color: var(--green); }
.diff-row.deleted .line-content::before { content: '- '; color: var(--red); }
</style>
