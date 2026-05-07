export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface SignupRequest {
  email: string
  password: string
}

export interface SignupResponse {
  id: number
  email: string
  status: UserStatus
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
  userId: number
  email: string
  role: UserRole
  status: UserStatus
}

export interface RefreshResponse {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
}

export interface MeResponse {
  userId: number
  email: string
  role: UserRole
  status: UserStatus
}

export interface AuthUser {
  userId: number
  email: string
  role: UserRole
  status: UserStatus
}

export interface PendingUser {
  userId: number
  email: string
  role: UserRole
  status: UserStatus
  createdAt: string
}
