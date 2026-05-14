<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="glow-sphere glow-1"></div>
      <div class="glow-sphere glow-2"></div>
    </div>
    
    <div class="login-container">
      <div class="login-header">
        <div class="logo-box">
          <svg width="32" height="32" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="8" fill="url(#login-grad)" />
            <path d="M8 14h5l2-5 2 10 2-5h1" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <defs>
              <linearGradient id="login-grad" x1="0" y1="0" x2="28" y2="28">
                <stop offset="0%" stop-color="#2563eb"/>
                <stop offset="100%" stop-color="#0ea5e9"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1 class="brand-name">SerialX</h1>
        <p class="brand-desc">统一分布式发号与治理平台</p>
      </div>

      <el-card class="login-card glass-card">
        <h2 class="card-title">欢迎登录</h2>
        
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入管理员账号"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入登录密码"
              :prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-link type="primary" :underline="false">忘记密码？</el-link>
          </div>

          <el-button
            type="primary"
            class="login-submit"
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            立即登录
          </el-button>
        </el-form>

      </el-card>

      <div class="login-footer">
        <p>© 2026 SerialX Team · Enterprise Infrastructure</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { login } from '@/api/auth'
import { setAccessToken, setCurrentUser } from '@/utils/auth'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(true)
const loginFormRef = ref<FormInstance>()

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  loading.value = true
  try {
    await loginFormRef.value.validate()
    
    const result = await login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })

    setAccessToken(result.accessToken)
    setCurrentUser(result.user)
    ElMessage.success('欢迎回来，' + (result.user.displayName || result.user.username))
    await router.push('/')
  } catch (error: any) {
    console.error('Login failed:', error)
    const msg = error.response?.data?.message || '登录失败，请检查后台服务或账号密码'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #0f172a;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.glow-sphere {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.4;
}

.glow-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--accent), transparent 70%);
  top: -100px;
  right: -100px;
}

.glow-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, #3b82f6, transparent 70%);
  bottom: -200px;
  left: -200px;
}

.login-container {
  width: 100%;
  max-width: 440px;
  padding: 20px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.login-header {
  text-align: center;
}

.logo-box {
  display: inline-flex;
  padding: 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 16px;
  box-shadow: var(--shadow-lg);
}

.brand-name {
  font-size: 32px;
  font-weight: 900;
  color: #fff;
  letter-spacing: -0.03em;
  margin: 0;
}

.brand-desc {
  font-size: 14px;
  color: #64748b;
  margin-top: 8px;
}

.login-card {
  padding: 32px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 32px;
  text-align: center;
  color: #fff;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.login-submit {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 0.1em;
  background: linear-gradient(135deg, var(--accent), #3b82f6);
  border: none;
}

.login-footer {
  text-align: center;
}

.login-footer p {
  font-size: 12px;
  color: #475569;
}

:deep(.el-form-item__label) {
  font-weight: 600 !important;
  color: #94a3b8 !important;
  padding-bottom: 4px !important;
}

:deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: none !important;
}

:deep(.el-input__inner) {
  color: #fff !important;
}

:deep(.el-checkbox__label) {
  color: #94a3b8 !important;
}
</style>
