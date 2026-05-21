export interface NamespaceRecord {
  id: number
  namespaceCode: string
  namespaceName: string
  idMode: number
  idModeName?: string
  status: number
  statusName?: string
  owner: string
  remark?: string | null
  currentVersion: number
  draftConfig?: Record<string, unknown> | null
  publishedConfig?: Record<string, unknown> | null
  createdAt?: string
  updatedAt?: string
}

export interface NamespaceQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: number
  idMode?: number
}

export interface NamespaceCreatePayload {
  namespaceCode: string
  namespaceName: string
  idMode: number
  owner: string
  remark?: string
  config: Record<string, unknown>
}

export interface NamespaceUpdatePayload {
  namespaceName: string
  owner: string
  remark?: string
}

export interface NamespaceDraftConfigPayload {
  idMode: number
  config: Record<string, unknown>
}

export interface NamespaceChangeLogRecord {
  id: number
  version: number
  actionType: number
  actionTypeName?: string
  beforeJson?: Record<string, unknown> | null
  afterJson?: Record<string, unknown> | null
  operator: string
  createdAt?: string
}

export interface NamespaceInstanceStatusRecord {
  instanceId: string
  serviceName: string
  host: string
  namespaceCode: string
  loadedVersion: number
  runtimeStatus: string
  lastReportTime?: string
  lastOfflineTime?: string
}

export interface NamespaceVersionRecord {
  version: number
  idMode: number
  idModeName?: string
  config?: Record<string, unknown> | null
  configChecksum?: string
  operator: string
  publishedAt?: string
  createdAt?: string
}
