import { request } from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { LoginPayload, LoginResponse } from '@/types/auth'
import type { AdminUser } from '@/types/admin-user'

/** 调用后台登录接口。 */
export const login = async (payload: LoginPayload): Promise<LoginResponse> => {
  const { data } = await request.post<ApiResponse<LoginResponse>>('/admin/auth/login', payload)
  return data.data
}

/** 查询当前登录用户信息。 */
export const fetchCurrentUser = async (): Promise<AdminUser> => {
  const { data } = await request.get<ApiResponse<AdminUser>>('/admin/auth/info')
  return data.data
}
