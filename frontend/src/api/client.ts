import axios, { AxiosError } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Send HTTP-Only auth cookies
});

// Request interceptor to attach Bearer token if stored in localStorage (for fallback)
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('roottrace_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle 401 unauth and format error messages
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<{ message?: string; error?: string }>) => {
    if (error.response?.status === 401) {
      // If we are on login page, don't loop
      if (!window.location.pathname.includes('/login')) {
        localStorage.removeItem('roottrace_token');
        localStorage.removeItem('roottrace_user');
        window.location.href = '/login?expired=true';
      }
    }
    return Promise.reject(error);
  }
);

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.data?.message) {
      return error.response.data.message;
    }
    if (error.response?.data?.error) {
      return error.response.data.error;
    }
    if (error.response?.status === 403) {
      return 'You do not have permission to perform this action.';
    }
    if (error.response?.status === 404) {
      return 'The requested resource was not found.';
    }
    if (error.response?.status === 500) {
      return 'An internal server error occurred. Please try again.';
    }
    if (error.code === 'ERR_NETWORK') {
      return 'Network connection failure. Backend may be offline.';
    }
  }
  return (error as Error)?.message || 'An unexpected error occurred.';
}
