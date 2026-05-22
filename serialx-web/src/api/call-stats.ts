import { request } from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { CallStatsQuery, CallStatsResponse } from '@/types/call-stats'

/** 查询发号调用统计。 */
export const fetchCallStats = async (params: CallStatsQuery): Promise<CallStatsResponse> => {
  const { data } = await request.get<ApiResponse<CallStatsResponse>>('/admin/call-stats', {
    params,
  })
  return data.data
}
