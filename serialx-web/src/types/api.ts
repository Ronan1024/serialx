export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}
