export interface ServiceInstanceNodeSummary {
  totalCount: number
  onlineCount: number
  offlineCount: number
  laggingNamespaceCount: number
}

export interface ServiceInstanceNodeNamespace {
  namespaceCode: string
  namespaceName?: string
  loadedVersion?: number
  currentVersion?: number
  runtimeStatus: string
  lagging: boolean
  lastReportTime?: string
  lastOfflineTime?: string
}

export interface ServiceInstanceNode {
  instanceId: string
  serviceName: string
  host: string
  runtimeStatus: string
  namespaceCount: number
  laggingNamespaceCount: number
  lastReportTime?: string
  lastOfflineTime?: string
  namespaces: ServiceInstanceNodeNamespace[]
}

export interface ServiceInstanceNodeList {
  summary: ServiceInstanceNodeSummary
  nodes: ServiceInstanceNode[]
}
