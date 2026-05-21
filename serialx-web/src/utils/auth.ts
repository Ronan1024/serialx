import { ACCESS_TOKEN_KEY, CURRENT_USER_KEY } from '@/constants/auth'
import type { AdminUser } from '@/types/admin-user'

/** 读取访问令牌。 */
export const getAccessToken = (): string => localStorage.getItem(ACCESS_TOKEN_KEY) || ''

/** 持久化访问令牌。 */
export const setAccessToken = (token: string): void => {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

/** 清理访问令牌。 */
export const clearAccessToken = (): void => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

/** 读取当前登录用户。 */
export const getCurrentUser = (): AdminUser | null => {
  const rawValue = localStorage.getItem(CURRENT_USER_KEY)

  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(rawValue) as AdminUser
  } catch {
    localStorage.removeItem(CURRENT_USER_KEY)
    return null
  }
}

/** 持久化当前登录用户。 */
export const setCurrentUser = (user: AdminUser): void => {
  localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user))
}

/** 清理当前登录用户。 */
export const clearCurrentUser = (): void => {
  localStorage.removeItem(CURRENT_USER_KEY)
}

/** 清理全部登录态。 */
export const clearAuthState = (): void => {
  clearAccessToken()
  clearCurrentUser()
}

/** 判断是否已登录。 */
export const isAuthenticated = (): boolean => Boolean(getAccessToken())
