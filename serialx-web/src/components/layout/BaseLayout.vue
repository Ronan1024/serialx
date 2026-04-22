<template>
  <el-container class="layout-container">
    <el-aside width="200px">
      <el-menu
        default-active="1"
        class="el-menu-vertical"
        :router="true"
      >
        <el-menu-item index="/">
          <el-icon><Menu /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>User Management</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>Settings</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-content-wrapper">
      <el-header>
        <div class="header-content">
          <div class="left-section">
            <span class="title">Serialx Admin</span>
            <el-breadcrumb separator="/" class="breadcrumb-container">
              <el-breadcrumb-item :to="{ path: '/' }">Home</el-breadcrumb-item>
              <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
                {{ item.meta.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="user-info">
            <el-dropdown>
              <span class="el-dropdown-link">
                Admin <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item>Profile</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">Logout</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, watchEffect } from 'vue'
import { useRouter, useRoute, type RouteLocationMatched } from 'vue-router'

const router = useRouter()
const route = useRoute()

const breadcrumbs = ref<RouteLocationMatched[]>([])

watchEffect(() => {
  breadcrumbs.value = route.matched.filter(item => item.meta && item.meta.title && item.path !== '/');
})

const handleLogout = () => {
  console.log('Logout clicked')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: row;
}

.el-aside {
  background-color: #304156;
  color: #fff;
  overflow-y: auto; /* Add scrollbar if content overflows */
}

.el-menu-vertical {
  height: 100%;
  border-right: none;
}

.main-content-wrapper {
  flex: 1; /* Allows this container to grow */
  display: flex;
  flex-direction: column;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px; /* Fixed height for header */
  flex-shrink: 0; /* Prevent header from shrinking */
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-section {
  display: flex;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  margin-right: 20px;
}

.breadcrumb-container {
  margin-left: 10px;
}

.user-info {
  cursor: pointer;
}

.el-main {
  flex: 1; /* Allows main to grow and take available space */
  padding: 20px;
  overflow-y: auto; /* Add scrollbar to main content if it overflows */
}
</style>
