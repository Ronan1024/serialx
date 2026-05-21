import { request } from '@/utils/request'
import type {
  NamespaceChangeLogRecord,
  NamespaceCreatePayload,
  NamespaceDraftConfigPayload,
  NamespaceInstanceStatusRecord,
  NamespaceQuery,
  NamespaceRecord,
  NamespaceUpdatePayload,
  NamespaceVersionRecord,
} from '@/types/namespace'
import type { ApiResponse, PageResult } from '@/types/api'

/** 分页查询 Namespace。 */
export const fetchNamespaces = async (
  params: NamespaceQuery,
): Promise<PageResult<NamespaceRecord>> => {
  const { data } = await request.get<ApiResponse<PageResult<NamespaceRecord>>>('/admin/namespaces', {
    params,
  })
  return data.data
}

/** 查询 Namespace 详情。 */
export const fetchNamespaceDetail = async (id: number): Promise<NamespaceRecord> => {
  const { data } = await request.get<ApiResponse<NamespaceRecord>>(`/admin/namespaces/${id}`)
  return data.data
}

/** 创建 Namespace。 */
export const createNamespace = async (
  payload: NamespaceCreatePayload,
): Promise<NamespaceRecord> => {
  const { data } = await request.post<ApiResponse<NamespaceRecord>>('/admin/namespaces', payload)
  return data.data
}

/** 更新 Namespace 基础信息。 */
export const updateNamespace = async (
  id: number,
  payload: NamespaceUpdatePayload,
): Promise<NamespaceRecord> => {
  const { data } = await request.put<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}`,
    payload,
  )
  return data.data
}

/** 保存 Namespace 草稿配置。 */
export const saveNamespaceDraftConfig = async (
  id: number,
  payload: NamespaceDraftConfigPayload,
): Promise<NamespaceRecord> => {
  const { data } = await request.put<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}/config`,
    payload,
  )
  return data.data
}

/** 发布 Namespace。 */
export const publishNamespace = async (id: number): Promise<NamespaceRecord> => {
  const { data } = await request.post<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}/publish`,
  )
  return data.data
}

/** 启用 Namespace。 */
export const enableNamespace = async (id: number): Promise<NamespaceRecord> => {
  const { data } = await request.post<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}/enable`,
  )
  return data.data
}

/** 停用 Namespace。 */
export const disableNamespace = async (id: number): Promise<NamespaceRecord> => {
  const { data } = await request.post<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}/disable`,
  )
  return data.data
}

/** 回滚 Namespace。 */
export const rollbackNamespace = async (
  id: number,
  version: number,
): Promise<NamespaceRecord> => {
  const { data } = await request.post<ApiResponse<NamespaceRecord>>(
    `/admin/namespaces/${id}/rollback/${version}`,
  )
  return data.data
}

/** 删除 Namespace。 */
export const deleteNamespace = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<null>>(`/admin/namespaces/${id}`)
}

/** 查询 Namespace 变更日志。 */
export const fetchNamespaceChangeLogs = async (
  id: number,
): Promise<NamespaceChangeLogRecord[]> => {
  const { data } = await request.get<ApiResponse<NamespaceChangeLogRecord[]>>(
    `/admin/namespaces/${id}/logs`,
  )
  return data.data
}

/** 查询 Namespace 实例状态。 */
export const fetchNamespaceInstances = async (
  id: number,
): Promise<NamespaceInstanceStatusRecord[]> => {
  const { data } = await request.get<ApiResponse<NamespaceInstanceStatusRecord[]>>(
    `/admin/namespaces/${id}/instances`,
  )
  return data.data
}

/** 查询 Namespace 已发布版本。 */
export const fetchNamespaceVersions = async (
  id: number,
): Promise<NamespaceVersionRecord[]> => {
  const { data } = await request.get<ApiResponse<NamespaceVersionRecord[]>>(
    `/admin/namespaces/${id}/versions`,
  )
  return data.data
}
