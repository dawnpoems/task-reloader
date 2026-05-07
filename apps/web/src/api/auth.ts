import { apiClient, type ApiResponse } from './client'
import type {
  LoginRequest,
  LoginResponse,
  MeResponse,
  PendingUser,
  RefreshResponse,
  SignupRequest,
  SignupResponse,
  UserStatus,
} from '../types/auth'

const NO_AUTH_OPTIONS = { skipAuth: true, retryOnUnauthorized: false } as const

export const authApi = {
  signup: (request: SignupRequest): Promise<ApiResponse<SignupResponse>> =>
    apiClient.post<SignupResponse>('/auth/signup', request, NO_AUTH_OPTIONS),

  login: (request: LoginRequest): Promise<ApiResponse<LoginResponse>> =>
    apiClient.post<LoginResponse>('/auth/login', request, NO_AUTH_OPTIONS),

  refresh: (): Promise<ApiResponse<RefreshResponse>> =>
    apiClient.post<RefreshResponse>('/auth/refresh', {}, NO_AUTH_OPTIONS),

  logout: (): Promise<ApiResponse<void>> =>
    apiClient.post<void>('/auth/logout', {}, NO_AUTH_OPTIONS),

  me: (): Promise<ApiResponse<MeResponse>> =>
    apiClient.get<MeResponse>('/auth/me'),

  getPendingUsers: (): Promise<ApiResponse<PendingUser[]>> =>
    apiClient.get<PendingUser[]>('/admin/users/pending'),

  getNonPendingUsers: (): Promise<ApiResponse<PendingUser[]>> =>
    apiClient.get<PendingUser[]>('/admin/users/non-pending'),

  updateUserStatus: (userId: number, status: UserStatus): Promise<ApiResponse<PendingUser>> =>
    apiClient.patch<PendingUser>(`/admin/users/${userId}/status`, { status }),

  approveUser: (userId: number): Promise<ApiResponse<PendingUser>> =>
    apiClient.post<PendingUser>(`/admin/users/${userId}/approve`, {}),

  rejectUser: (userId: number): Promise<ApiResponse<PendingUser>> =>
    apiClient.post<PendingUser>(`/admin/users/${userId}/reject`, {}),
}
