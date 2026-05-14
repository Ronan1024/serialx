<template>
  <div class="user-management">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理后台管理员账户、角色权限及状态</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">添加用户</el-button>
      </div>
    </div>

    <!-- Filter Card -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="搜索">
          <el-input
            v-model="queryForm.keyword"
            placeholder="用户名 / 姓名 / 邮箱"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
      <el-table
        v-loading="loading"
        :data="users"
        style="width: 100%"
        row-class-name="user-row"
      >
        <el-table-column label="用户信息" min-width="240">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-wrap">
                <div class="user-avatar-small" :style="getAvatarStyle(row)">
                  {{ (row.displayName || row.username)[0].toUpperCase() }}
                </div>
              </div>
              <div class="user-meta">
                <div class="user-name-text">{{ row.displayName || '未设置姓名' }}</div>
                <div class="user-username-text">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="角色" width="160">
          <template #default="{ row }">
            <el-tag
              v-for="role in (row.roles || 'ADMIN').split(',')"
              :key="role"
              size="small"
              class="role-tag"
            >
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val: number) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="180">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.lastLoginAt || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="primary" @click="openPasswordDialog(row)">重置密码</el-button>
              <el-button
                link
                type="danger"
                :disabled="row.username === 'admin'"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '添加用户' : '编辑用户'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="userForm.username"
            placeholder="登录账号"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始密码" prop="password">
          <el-input v-model="userForm.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="userForm.displayName" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="电子邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="userForm.roles" placeholder="选择角色" style="width: 100%">
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="OPERATOR" value="OPERATOR" />
            <el-option label="VIEWER" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- Password Dialog -->
    <el-dialog v-model="passwordVisible" title="重置用户密码" width="400px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handlePasswordSubmit">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchAdminUsers, createAdminUser, updateAdminUser, deleteAdminUser, updateAdminUserPassword } from '@/api/admin-user'
import type { AdminUser, AdminUserQuery } from '@/types/admin-user'

const loading = ref(false)
const submitting = ref(false)
const total = ref(0)
const users = ref<AdminUser[]>([])

const queryForm = reactive<AdminUserQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined
})

const loadUsers = async () => {
  loading.value = true
  try {
    const data = await fetchAdminUsers(queryForm)
    users.value = data.records
    total.value = data.total
  } catch (err: any) {
    console.error('Fetch users failed:', err)
    ElMessage.error(err.response?.data?.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

const handleSearch = () => {
  queryForm.pageNo = 1
  loadUsers()
}

const handleReset = () => {
  queryForm.keyword = ''
  queryForm.status = undefined
  handleSearch()
}

// Dialog Logic
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const userFormRef = ref<FormInstance>()
const userForm = reactive({
  id: 0,
  username: '',
  password: '',
  displayName: '',
  email: '',
  roles: 'ADMIN',
  status: 1
})

const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, { min: 6, message: '至少 6 位', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }]
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  Object.assign(userForm, { id: 0, username: '', password: '', displayName: '', email: '', roles: 'ADMIN', status: 1 })
  dialogVisible.value = true
}

const openEditDialog = (row: AdminUser) => {
  dialogMode.value = 'edit'
  Object.assign(userForm, { ...row })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!userFormRef.value) return
  await userFormRef.value.validate()
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAdminUser(userForm)
      ElMessage.success('用户创建成功')
    } else {
      await updateAdminUser(userForm.id, { 
        displayName: userForm.displayName, 
        email: userForm.email, 
        status: userForm.status,
        roles: userForm.roles
      })
      ElMessage.success('用户信息已更新')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (err: any) {
    console.error('Submit failed:', err)
    ElMessage.error(err.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// Password Logic
const passwordVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ id: 0, password: '' })
const passwordRules: FormRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, message: '至少 6 位', trigger: 'blur' }]
}

const openPasswordDialog = (row: AdminUser) => {
  passwordForm.id = row.id
  passwordForm.password = ''
  passwordVisible.value = true
}

const handlePasswordSubmit = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate()
  submitting.value = true
  try {
    await updateAdminUserPassword(passwordForm.id, { password: passwordForm.password })
    ElMessage.success('密码重置成功')
    passwordVisible.value = false
  } catch (err: any) {
    console.error('Password reset failed:', err)
    ElMessage.error(err.response?.data?.message || '密码重置失败')
  } finally {
    submitting.value = false
  }
}

const handleStatusChange = async (row: AdminUser, status: number) => {
  try {
    await updateAdminUser(row.id, { ...row, status })
    ElMessage.success(`用户已${status ? '启用' : '禁用'}`)
  } catch (err: any) {
    row.status = status ? 0 : 1 // revert
    console.error('Status change failed:', err)
    ElMessage.error(err.response?.data?.message || '状态更新失败')
  }
}

const handleDelete = (row: AdminUser) => {
  ElMessageBox.confirm(`确定删除用户 @${row.username} 吗？此操作不可撤销。`, '安全警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      await deleteAdminUser(row.id)
      ElMessage.success('用户已删除')
      loadUsers()
    } catch (err: any) {
      console.error('Delete failed:', err)
      ElMessage.error(err.response?.data?.message || '删除失败')
    }
  }).catch(() => {})
}

// Styling Helper
const getAvatarStyle = (user: AdminUser) => {
  const colors = ['#6366f1', '#8b5cf6', '#3b82f6', '#22c55e', '#f59e0b', '#ef4444']
  const index = user.username.length % colors.length
  return { background: colors[index] }
}
</script>

<style scoped>
.user-management {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.filter-card {
  margin-bottom: 20px;
}

.filter-form {
  margin-bottom: -18px;
}

.table-card {
  padding: 0;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar-wrap {
  flex-shrink: 0;
}

.user-avatar-small {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.user-name-text {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}

.user-username-text {
  font-size: 12px;
  color: var(--text-muted);
}

.role-tag {
  margin-right: 4px;
  border: none;
  background: var(--bg-page);
  color: var(--text-secondary);
}

.table-footer {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.user-row:hover) {
  background-color: var(--bg-card2) !important;
}

:deep(.el-table__fixed-right) {
  height: 100% !important;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
