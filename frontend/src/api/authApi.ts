import { apiClient } from './client';
import type { User } from '../types';

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  login: async (payload: LoginPayload): Promise<void> => {
    await apiClient.post('/api/auth/login', payload);
  },

  refresh: async (): Promise<void> => {
    await apiClient.post('/api/auth/refresh');
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/api/auth/logout');
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<User>('/api/auth/me');
    return response.data;
  },
};
