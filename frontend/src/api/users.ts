import axios from 'axios';
import type { UserRole } from '@/context/AuthContext';

export interface AppUser {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface CreateUserPayload {
  email: string;
  fullName: string;
  role: UserRole;
  initialPassword: string;
}

export const fetchUsers = (): Promise<AppUser[]> =>
  axios.get<AppUser[]>('/api/users').then((r) => r.data);

export const fetchMe = (): Promise<AppUser> =>
  axios.get<AppUser>('/api/users/me').then((r) => r.data);

export const createUser = (payload: CreateUserPayload): Promise<AppUser> =>
  axios.post<AppUser>('/api/users', payload).then((r) => r.data);

export const updateUserRole = (id: string, role: UserRole): Promise<AppUser> =>
  axios.put<AppUser>(`/api/users/${id}/role`, { role }).then((r) => r.data);

export const deactivateUser = (id: string): Promise<AppUser> =>
  axios.put<AppUser>(`/api/users/${id}/deactivate`).then((r) => r.data);

export const reactivateUser = (id: string): Promise<AppUser> =>
  axios.put<AppUser>(`/api/users/${id}/reactivate`).then((r) => r.data);

export const resetUserPassword = (id: string, newPassword: string): Promise<AppUser> =>
  axios
    .put<AppUser>(`/api/users/${id}/reset-password`, { newPassword })
    .then((r) => r.data);

export const changeOwnPassword = (
  currentPassword: string,
  newPassword: string,
): Promise<AppUser> =>
  axios
    .put<AppUser>('/api/users/me/password', { currentPassword, newPassword })
    .then((r) => r.data);
