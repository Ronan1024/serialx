import type { AdminUser } from './admin-user'

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: AdminUser
  loginAt: string
}
