<template>
  <div class="user-management">
    <h1>User Management</h1>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>User List</span>
          <el-button type="primary" @click="handleAddUser">Add User</el-button>
        </div>
      </template>

      <el-table :data="users" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80"></el-table-column>
        <el-table-column prop="username" label="Username"></el-table-column>
        <el-table-column prop="email" label="Email"></el-table-column>
        <el-table-column prop="status" label="Status">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Operations" width="180">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">Edit</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface User {
  id: number;
  username: string;
  email: string;
  status: string;
}

const users = ref<User[]>([
  { id: 1, username: 'admin', email: 'admin@example.com', status: 'active' },
  { id: 2, username: 'testuser', email: 'test@example.com', status: 'inactive' },
  { id: 3, username: 'another', email: 'another@example.com', status: 'active' },
]);

const handleAddUser = () => {
  ElMessage.info('Add User clicked')
  // Implement add user logic
};

const handleEdit = (row: User) => {
  ElMessage.info(`Edit user: ${row.username}`)
  // Implement edit user logic
};

const handleDelete = (row: User) => {
  ElMessage.warning(`Delete user: ${row.username}`)
  // Implement delete user logic
};
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
