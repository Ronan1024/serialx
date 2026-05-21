import axios from 'axios'
import { ElMessage } from 'element-plus'

import { API_BASE_URL, SERVICE_API_BASE_URL } from '@/config/api'
import { clearAuthState, getAccessToken } from '@/utils/auth'

const createRequest = (baseURL: string) => {
  const instance = axios.create({
    baseURL,
    timeout: 10000,
  })

  instance.interceptors.request.use((config) => {
    const token = getAccessToken()

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const responseBody = response.data as { code?: string; message?: string } | undefined

      if (responseBody && responseBody.code && responseBody.code !== '0') {
        ElMessage.error(responseBody.message || '请求失败')
        return Promise.reject(new Error(responseBody.message || 'Request failed'))
      }

      return response
    },
    (error) => {
      const status = error.response?.status as number | undefined
      const message =
        (error.response?.data as { message?: string } | undefined)?.message ||
        error.message ||
        '请求失败'

      if (status === 401) {
        clearAuthState()
        ElMessage.error('登录状态已失效，请重新登录')

        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }

        return Promise.reject(error)
      }

      ElMessage.error(message)
      return Promise.reject(error)
    },
  )

  return instance
}

/** 后台管理接口统一请求实例。 */
export const request = createRequest(API_BASE_URL)

/** 发号服务接口统一请求实例。 */
export const serviceRequest = createRequest(SERVICE_API_BASE_URL)
