<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code">403</div>
      <h1 class="error-title">拒绝访问</h1>
      <p class="error-desc">您没有权限访问该资源。请联系管理员申请授权。</p>
      <div class="error-actions">
        <el-button type="primary" size="large" @click="$router.push('/')">返回首页</el-button>
        <el-button size="large" @click="handleApply">申请权限</el-button>
      </div>
    </div>
    <div class="error-illustration">
      <el-icon size="200" color="var(--red)" style="opacity: 0.1"><Lock /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const handleApply = () => {
  ElMessage.success('权限申请已提交，请等待审批')
}
</script>

<style scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  background: var(--bg-page);
  padding: 40px;
}

.error-code {
  font-size: 120px;
  font-weight: 900;
  color: var(--red);
  line-height: 1;
  margin-bottom: 24px;
  letter-spacing: -0.05em;
  opacity: 0.2;
}

.error-title {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.error-desc {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 40px;
}

.error-actions {
  display: flex;
  gap: 16px;
}
</style>
