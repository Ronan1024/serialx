import { createRouter, createWebHistory } from 'vue-router'
import BaseLayout from '../components/layout/BaseLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
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
          meta: { title: 'Dashboard' }
        },
        {
          path: 'users',
          name: 'users',
          component: UserManagementView,
          meta: { title: 'User Management' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/AboutView.vue'), // Placeholder for Settings
          meta: { title: 'Settings' }
        }
      ]
    }
  ]
})

export default router
