export interface IdGenerateRequest {
  namespaceCode: string
}

export interface IdGenerateResponse {
  namespaceCode: string
  version: number
  id: string
}

export interface IdBatchGenerateRequest {
  namespaceCode: string
  count: number
}

export interface IdBatchGenerateResponse {
  namespaceCode: string
  version: number
  ids: string[]
}

export interface NamespaceRuntimeStatusResponse {
  namespaceCode: string
  version: number
  idMode: number
  runtimeStatus: string
  refreshedAt?: string
}
