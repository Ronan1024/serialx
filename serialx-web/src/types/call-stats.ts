export interface CallStatsQuery {
  startTime?: string
  endTime?: string
  namespaceCode?: string
  instanceId?: string
  clientId?: string
  clientName?: string
}

export interface CallStatsSummary {
  totalRequests: number
  successRequests: number
  failedRequests: number
  totalIds: number
  avgQps: number
  avgLatencyMs: number
  maxLatencyMs: number
  errorRate: number
  onlineInstanceCount: number
  avgLoadRate: number
}

export interface CallStatsTrendPoint {
  time: string
  totalRequests: number
  successRequests: number
  failedRequests: number
  avgLatencyMs: number
}

export interface CallStatsInstance {
  instanceId: string
  host?: string
  runtimeStatus: string
  lastReportTime?: string
  namespaceCount: number
  laggingNamespaceCount: number
  requestCount: number
  successCount: number
  failedCount: number
  idCount: number
  currentQps: number
  avgLatencyMs: number
  maxLatencyMs: number
  errorRate: number
  maxQps?: number
  loadRate?: number
}

export interface CallStatsNamespace {
  namespaceCode: string
  requestCount: number
  successCount: number
  failedCount: number
  idCount: number
  avgQps: number
  avgLatencyMs: number
  maxLatencyMs: number
  errorRate: number
  instanceCount: number
}

export interface CallStatsClient {
  clientId: string
  clientName: string
  requestCount: number
  successCount: number
  failedCount: number
  idCount: number
  avgQps: number
  avgLatencyMs: number
  maxLatencyMs: number
  errorRate: number
}

export interface CallStatsError {
  errorCode: string
  count: number
  percentage: number
}

export interface CallStatsResponse {
  startTime: string
  endTime: string
  summary: CallStatsSummary
  trend: CallStatsTrendPoint[]
  instances: CallStatsInstance[]
  namespaces: CallStatsNamespace[]
  clients: CallStatsClient[]
  errors: CallStatsError[]
}
