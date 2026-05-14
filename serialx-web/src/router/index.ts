import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'
import BaseLayout from '../components/layout/BaseLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import NamespaceManagementView from '../views/NamespaceManagementView.vue'
import UserManagementView from '../views/UserManagementView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/',
      component: BaseLayout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: DashboardView,
          meta: { title: '概览' }
        },
        {
          path: 'namespaces',
          name: 'namespaces',
          component: NamespaceManagementView,
          meta: { title: 'Namespace 管理' }
        },
        {
          path: 'apps',
          name: 'apps',
          component: () => import('../views/AppAccessView.vue'),
          meta: { title: '应用接入' }
        },
        {
          path: 'nodes',
          name: 'nodes',
          component: () => import('../views/NodeManagementView.vue'),
          meta: { title: '节点管理' }
        },
        {
          path: 'stats',
          name: 'stats',
          component: () => import('../views/StatsView.vue'),
          meta: { title: '调用统计' }
        },
        {
          path: 'alarms',
          name: 'alarms',
          component: () => import('../views/AlarmsView.vue'),
          meta: { title: '告警中心' }
        },
        {
          path: 'approvals',
          name: 'approvals',
          component: () => import('../views/ApprovalView.vue'),
          meta: { title: '审批中心' }
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('../views/AuditLogView.vue'),
          meta: { title: '审计日志' }
        },
        {
          path: 'users',
          name: 'users',
          component: UserManagementView,
          meta: { title: '用户管理' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/AboutView.vue'),
          meta: { title: '系统设置' }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../views/ProfileView.vue'),
          meta: { title: '个人资料' }
        },
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('../views/ForbiddenView.vue'),
          meta: { title: '无权限' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue')
    }
  ]
})

router.beforeEach((to) => {
  if (to.path === '/login') {
    if (isAuthenticated()) {
      return '/'
    }
    return true
  }

  if (!isAuthenticated()) {
    return '/login'
  }

  return true
})

export default router
