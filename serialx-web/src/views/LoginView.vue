<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span class="title">Serialx Admin Login</span>
        </div>
      </template>
      <el-form :model="loginForm" ref="loginFormRef" label-width="100px" :rules="rules">
        <el-form-item label="Username" prop="username">
          <el-input v-model="loginForm.username"></el-input>
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input type="password" v-model="loginForm.password"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading">Login</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const loading = ref(false)

const loginForm = ref({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: 'Please input username', trigger: 'blur' },
    { min: 3, max: 15, message: 'Length should be 3 to 15', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please input password', trigger: 'blur' },
    { min: 6, max: 20, message: 'Length should be 6 to 20', trigger: 'blur' }
  ]
}

const loginFormRef = ref<FormInstance>()

const handleLogin = async () => {
  loading.value = true
  try {
    await loginFormRef.value?.validate()
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1000))
    console.log('Login successful:', loginForm.value)
    router.push('/') // Redirect to dashboard on successful login
  } catch (error) {
    console.error('Login failed:', error)
    // Handle login error display if needed
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5;
}

.login-card {
  width: 400px;
}

.card-header {
  text-align: center;
}

.title {
  font-size: 20px;
  font-weight: bold;
}
</style>
