import { serviceRequest } from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  IdBatchGenerateRequest,
  IdBatchGenerateResponse,
  IdGenerateRequest,
  IdGenerateResponse,
  NamespaceRuntimeStatusResponse,
} from '@/types/id'

/** 获取单个业务 ID。 */
export const generateId = async (payload: IdGenerateRequest): Promise<IdGenerateResponse> => {
  const { data } = await serviceRequest.post<ApiResponse<IdGenerateResponse>>('/ids/next', payload)
  return data.data
}

/** 批量获取业务 ID。 */
export const batchGenerateIds = async (
  payload: IdBatchGenerateRequest,
): Promise<IdBatchGenerateResponse> => {
  const { data } = await serviceRequest.post<ApiResponse<IdBatchGenerateResponse>>(
    '/ids/batch',
    payload,
  )
  return data.data
}

/** 查询 Namespace 在发号服务中的运行态。 */
export const fetchNamespaceRuntimeStatus = async (
  namespaceCode: string,
): Promise<NamespaceRuntimeStatusResponse> => {
  const { data } = await serviceRequest.get<ApiResponse<NamespaceRuntimeStatusResponse>>(
    `/ids/namespaces/${namespaceCode}/status`,
  )
  return data.data
}
