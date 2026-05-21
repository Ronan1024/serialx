import { request } from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { ServiceInstanceNodeList } from '@/types/service-instance'

/** 查询 Service 节点列表。 */
export const fetchServiceInstanceNodes = async (): Promise<ServiceInstanceNodeList> => {
  const { data } = await request.get<ApiResponse<ServiceInstanceNodeList>>(
    '/admin/service-instances/nodes',
  )
  return data.data
}
