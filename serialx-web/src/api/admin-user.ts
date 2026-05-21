import { request } from '@/utils/request'
import type {
  AdminUser,
  AdminUserCreatePayload,
  AdminUserPasswordPayload,
  AdminUserQuery,
  AdminUserUpdatePayload,
} from '@/types/admin-user'
import type { ApiResponse, PageResult } from '@/types/api'

/** 分页查询后台用户。 */
export const fetchAdminUsers = async (params: AdminUserQuery): Promise<PageResult<AdminUser>> => {
  const { data } = await request.get<ApiResponse<PageResult<AdminUser>>>('/admin/users', { params })
  return data.data
}

/** 创建后台用户。 */
export const createAdminUser = async (payload: AdminUserCreatePayload): Promise<AdminUser> => {
  const { data } = await request.post<ApiResponse<AdminUser>>('/admin/users', payload)
  return data.data
}

/** 更新后台用户。 */
export const updateAdminUser = async (
  id: number,
  payload: AdminUserUpdatePayload,
): Promise<AdminUser> => {
  const { data } = await request.put<ApiResponse<AdminUser>>(`/admin/users/${id}`, payload)
  return data.data
}

/** 更新后台用户密码。 */
export const updateAdminUserPassword = async (
  id: number,
  payload: AdminUserPasswordPayload,
): Promise<void> => {
  await request.put<ApiResponse<null>>(`/admin/users/${id}/password`, payload)
}

/** 删除后台用户。 */
export const deleteAdminUser = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<null>>(`/admin/users/${id}`)
}
