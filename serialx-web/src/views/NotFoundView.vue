<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code">404</div>
      <h1 class="error-title">页面未找到</h1>
      <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
      <div class="error-actions">
        <el-button type="primary" size="large" @click="$router.push('/')">返回首页</el-button>
        <el-button size="large" @click="$router.back()">返回上一页</el-button>
      </div>
    </div>
    <div class="error-illustration">
      <svg width="400" height="300" viewBox="0 0 400 300" fill="none">
        <circle cx="200" cy="150" r="100" fill="var(--accent-soft)" />
        <path d="M150 150l20-20 30 30 50-50" stroke="var(--accent)" stroke-width="8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
  </div>
</template>

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
  color: var(--accent);
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

.error-illustration {
  opacity: 0.8;
}
</style>
