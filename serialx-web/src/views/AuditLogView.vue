<template>
  <div class="audit-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">审计日志</h1>
        <p class="page-subtitle">追溯系统所有登录行为及配置变更记录，确保操作可审计、可追责</p>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true">
        <el-form-item label="操作人">
          <el-input placeholder="用户名" style="width: 150px" />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select placeholder="全部动作" style="width: 150px">
            <el-option label="登录" value="LOGIN" />
            <el-option label="创建" value="CREATE" />
            <el-option label="修改" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="发布" value="PUBLISH" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">搜索</el-button>
          <el-button>导出日志</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt-20">
      <el-table :data="logs" style="width: 100%">
        <el-table-column prop="time" label="操作时间" width="180" />
        <el-table-column prop="user" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" width="120">
          <template #default="{ row }">
            <el-tag :type="actionColor(row.action)" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="操作对象" width="180" />
        <el-table-column prop="detail" label="详情说明" min-width="200" />
        <el-table-column prop="ip" label="来源 IP" width="140" />
      </el-table>
      <div class="pagination-box mt-20">
        <el-pagination layout="total, prev, pager, next" :total="500" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
const logs = [
  { time: '2026-05-14 13:45:10', user: 'admin', action: 'PUBLISH', target: 'order_id', detail: '发布版本 v13', ip: '192.168.1.102' },
  { time: '2026-05-14 13:30:05', user: 'ronan', action: 'UPDATE', target: 'user_profile', detail: '修改号段步长为 2000', ip: '10.0.5.21' },
  { time: '2026-05-14 12:10:44', user: 'jiang', action: 'LOGIN', target: 'System', detail: '控制台登录成功', ip: '127.0.0.1' },
  { time: '2026-05-14 11:20:15', user: 'admin', action: 'DELETE', target: 'test_ns', detail: '删除了测试命名空间', ip: '192.168.1.102' },
]

const actionColor = (a: string) => {
  const map: any = { 'PUBLISH': 'success', 'UPDATE': 'primary', 'DELETE': 'danger', 'LOGIN': 'info' }
  return map[a] || 'info'
}
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 700; }
.page-subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }
.filter-card { margin-bottom: 20px; }
.pagination-box { display: flex; justify-content: flex-end; }
</style>
