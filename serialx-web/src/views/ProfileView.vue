<template>
  <div class="profile-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">管理个人账号信息、修改密码及配置安全设置</p>
      </div>
    </div>

    <el-row :gutter="24">
      <el-col :span="8">
        <el-card shadow="never" class="user-info-card">
          <div class="user-avatar-large">{{ userInitial }}</div>
          <h2 class="u-name">{{ user.displayName }}</h2>
          <p class="u-role">系统管理员 (Super Admin)</p>
          <el-divider />
          <div class="u-stats">
            <div class="u-stat">
              <span class="u-stat-val">42</span>
              <span class="u-stat-label">管理资源</span>
            </div>
            <div class="u-stat">
              <span class="u-stat-val">128</span>
              <span class="u-stat-label">操作记录</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本设置" name="basic">
              <el-form label-position="top" class="mt-20">
                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="用户名">
                      <el-input v-model="user.username" disabled />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="显示名称">
                      <el-input v-model="user.displayName" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="电子邮箱">
                  <el-input v-model="user.email" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary">保存更改</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="安全设置" name="security">
              <div class="security-list mt-20">
                <div class="sec-item">
                  <div class="sec-main">
                    <div class="sec-title">账号密码</div>
                    <div class="sec-desc">定期修改密码可提高账号安全性</div>
                  </div>
                  <el-button link type="primary">修改密码</el-button>
                </div>
                <el-divider />
                <div class="sec-item">
                  <div class="sec-main">
                    <div class="sec-title">多因素认证 (MFA)</div>
                    <div class="sec-desc">未绑定安全令牌或二次验证</div>
                  </div>
                  <el-button link type="primary">立即绑定</el-button>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getCurrentUser } from '@/utils/auth'

const user = ref(getCurrentUser() || { username: 'admin', displayName: '超级管理员', email: 'admin@serialx.io' })
const userInitial = computed(() => {
  const name = user.value.displayName || user.value.username || 'A'
  return name.charAt(0).toUpperCase() || 'A'
})
const activeTab = ref('basic')
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 700; }
.page-subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }

.user-info-card { text-align: center; padding: 20px; }
.user-avatar-large {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--accent-from), var(--accent-to));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 800;
  margin: 0 auto 20px;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.2);
}
.u-name { font-size: 20px; font-weight: 700; color: var(--text-primary); margin: 0; }
.u-role { font-size: 13px; color: var(--text-muted); margin-top: 4px; }
.u-stats { display: flex; justify-content: center; gap: 40px; margin-top: 20px; }
.u-stat { display: flex; flex-direction: column; }
.u-stat-val { font-size: 18px; font-weight: 700; color: var(--text-primary); }
.u-stat-label { font-size: 11px; color: var(--text-muted); }

.sec-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; }
.sec-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.sec-desc { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
</style>
