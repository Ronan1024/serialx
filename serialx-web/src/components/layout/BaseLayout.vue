<template>
  <div class="app-shell">
    <!-- ── Sidebar ────────────────────────────────── -->
    <aside class="sidebar">
      <!-- Brand -->
      <div class="sidebar-brand">
        <div class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="8" fill="url(#brand-grad)" />
            <path d="M8 14h5l2-5 2 10 2-5h1" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <defs>
              <linearGradient id="brand-grad" x1="0" y1="0" x2="28" y2="28">
                <stop offset="0%" stop-color="#2563eb"/>
                <stop offset="100%" stop-color="#0ea5e9"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">SerialX</span>
          <span class="brand-tag">Governance</span>
        </div>
      </div>

      <!-- Nav -->
      <nav class="sidebar-nav">
        <div v-for="section in menuGroups" :key="section.title" class="nav-group">
          <div class="nav-group-title">{{ section.title }}</div>
          <router-link
            v-for="item in section.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ 'nav-item--active': isActive(item.path) }"
          >
            <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.badge" class="nav-badge" :class="item.badgeType">{{ item.badge }}</span>
          </router-link>
        </div>
      </nav>

      <!-- User footer -->
      <div class="sidebar-footer">
        <el-dropdown trigger="click" placement="top-start" class="user-dropdown">
          <div class="sidebar-user">
            <div class="user-avatar">{{ userInitial }}</div>
            <div class="user-info">
              <div class="user-name">{{ currentUserName }}</div>
              <div class="user-role">系统管理员</div>
            </div>
            <el-icon class="user-more"><CaretTop /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="premium-dropdown">
              <el-dropdown-item :icon="User" @click="router.push('/profile')">个人资料</el-dropdown-item>
              <el-dropdown-item :icon="Setting" @click="router.push('/settings')">偏好设置</el-dropdown-item>
              <el-dropdown-item :icon="SwitchButton" divided @click="handleLogout" class="logout-item">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <!-- ── Main Area ──────────────────────────────────── -->
    <div class="main-area">
      <!-- Topbar -->
      <header class="topbar">
        <div class="topbar-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">控制台</el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="crumb in breadcrumbs"
              :key="crumb.path"
            >{{ crumb.meta?.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <div class="topbar-actions">
            <el-tooltip content="搜索资源" placement="bottom">
              <el-button :icon="Search" circle text />
            </el-tooltip>
            <el-tooltip content="系统通知" placement="bottom">
              <div class="notification-wrap">
                <el-button :icon="Bell" circle text />
                <span class="dot-badge"></span>
              </div>
            </el-tooltip>
            <el-tooltip content="帮助文档" placement="bottom">
              <el-button :icon="QuestionFilled" circle text />
            </el-tooltip>
          </div>
          <el-divider direction="vertical" />
          <div class="system-status">
            <span class="status-dot online"></span>
            <span class="status-text">集群运行中</span>
          </div>
        </div>
      </header>

      <!-- Page Content -->
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <div :key="route.path" class="slide-up">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watchEffect, ref } from 'vue'
import { useRouter, useRoute, type RouteLocationMatched } from 'vue-router'
import {
  DataAnalysis, Grid, Connection, Monitor, PieChart, 
  Bell, DocumentChecked, List, Setting, 
  Search, QuestionFilled, CaretTop, User, SwitchButton
} from '@element-plus/icons-vue'
import { clearAuthState, getCurrentUser } from '@/utils/auth'

const router = useRouter()
const route  = useRoute()

const currentUser = ref(getCurrentUser())
const currentUserName = computed(() =>
  currentUser.value?.displayName || currentUser.value?.username || 'Admin'
)
const userInitial = computed(() => (currentUserName.value?.[0] ?? 'A').toUpperCase())

const breadcrumbs = ref<RouteLocationMatched[]>([])
watchEffect(() => {
  currentUser.value = getCurrentUser()
  breadcrumbs.value = route.matched.filter(
    (item) => item.meta?.title && item.path !== '/'
  )
})

const menuGroups = [
  {
    title: '核心功能',
    items: [
      { path: '/',           label: '概览仪表盘',   icon: DataAnalysis },
      { path: '/namespaces', label: 'Namespace 管理', icon: Grid },
      { path: '/apps',       label: '应用接入',     icon: Connection },
    ]
  },
  {
    title: '运维治理',
    items: [
      { path: '/nodes',      label: '节点管理',     icon: Monitor, badge: '3', badgeType: 'success' },
      { path: '/stats',      label: '调用统计',     icon: PieChart },
      { path: '/alarms',     label: '告警中心',     icon: Bell, badge: '!', badgeType: 'danger' },
    ]
  },
  {
    title: '管控中心',
    items: [
      { path: '/approvals',  label: '审批中心',     icon: DocumentChecked, badge: '2', badgeType: 'warning' },
      { path: '/audit',      label: '审计日志',     icon: List },
      { path: '/settings',   label: '系统设置',     icon: Setting },
    ]
  }
]

function isActive(path: string) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const handleLogout = () => {
  clearAuthState()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ── Sidebar ── */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  z-index: 100;
}

.sidebar-brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-name {
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}

.brand-tag {
  font-size: 10px;
  font-weight: 600;
  color: var(--accent);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.sidebar-nav {
  flex: 1;
  padding: 24px 12px;
  overflow-y: auto;
}

.nav-group {
  margin-bottom: 24px;
}

.nav-group-title {
  padding: 0 12px 8px;
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  margin-bottom: 2px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: var(--transition);
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
}

.nav-item--active {
  background: rgba(37, 99, 235, 0.15);
  color: #fff;
  font-weight: 600;
}

.nav-item--active .nav-icon {
  color: var(--accent);
}

.nav-badge {
  margin-left: auto;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-weight: 700;
}
.nav-badge.success { background: var(--green); }
.nav-badge.danger { background: var(--red); }

/* User Footer */
.sidebar-footer {
  padding: 16px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: var(--transition);
}

.sidebar-user:hover {
  background: rgba(255, 255, 255, 0.05);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent-from), var(--accent-to));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
}

.user-more {
  color: rgba(255, 255, 255, 0.3);
  font-size: 14px;
}

/* ── Main Area ── */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notification-wrap {
  position: relative;
}

.dot-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  background: var(--red);
  border: 2px solid #fff;
  border-radius: 50%;
}

.system-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.status-dot.online {
  background: var(--green);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
}

.status-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* Transitions */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

.premium-dropdown {
  padding: 6px;
  border-radius: 12px;
}

.logout-item {
  color: var(--red) !important;
}
</style>
