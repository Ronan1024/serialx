export interface AdminUser {
  id: number
  username: string
  displayName?: string
  email?: string
  mobile?: string
  status: number
  roles?: string
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminUserQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: number
}

export interface AdminUserCreatePayload {
  username: string
  password: string
  displayName?: string
  email?: string
  mobile?: string
  roles?: string
}

export interface AdminUserUpdatePayload {
  displayName?: string
  email?: string
  mobile?: string
  status: number
  roles?: string
}

export interface AdminUserPasswordPayload {
  password: string
}
