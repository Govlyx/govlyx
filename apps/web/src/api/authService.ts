import axiosInstance from './axiosConfig';
import type {
  RegisterRequest,
  AuthRequest,
  ApiResponse,
  AuthResponse,
} from '../types/auth.types';

// POST /api/auth/register/citizen
export const registerCitizen = async (
  data: RegisterRequest,
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    '/api/auth/register/citizen',
    data,
  );
  return response.data;
};

// POST /api/auth/register/department
export const registerDepartment = async (
  data: RegisterRequest,
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    '/api/auth/register/department',
    data,
  );
  return response.data;
};

// POST /api/auth/login
export const loginUser = async (
  data: AuthRequest,
): Promise<ApiResponse<AuthResponse>> => {
  const response = await axiosInstance.post<ApiResponse<AuthResponse>>(
    '/api/auth/login',
    data,
  );
  return response.data;
};

// GET /api/auth/verify-email
export const verifyEmail = async (
  token: string,
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.get<ApiResponse<string>>(
    `/api/auth/verify-email?token=${encodeURIComponent(token)}`,
  );
  return response.data;
};

// POST /api/auth/resend-verification
export const resendVerification = async (
  email: string,
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    `/api/auth/resend-verification?email=${encodeURIComponent(email.trim())}`,
  );
  return response.data;
};

// POST /api/auth/vault-blob (save/update encrypted vault blob)
export const saveVaultBlob = async (data: {
  vaultBlob: string;
  vaultSalt?: string;
  actorToken?: string;
  clientSalt?: string;
  username?: string;
}): Promise<ApiResponse<any>> => {
  const response = await axiosInstance.post<ApiResponse<any>>(
    '/api/auth/vault-blob',
    data,
  );
  return response.data;
};

// POST /api/auth/vault/setup (initial citizen vault setup)
export const setupVault = async (data: {
  clientSalt: string;
  vaultBlob: string;
  vaultSalt: string;
  actorToken?: string;
  username?: string;
}): Promise<ApiResponse<any>> => {
  const response = await axiosInstance.post<ApiResponse<any>>(
    '/api/auth/vault/setup',
    data,
  );
  return response.data;
};

// POST /api/auth/reset-vault (reset citizen vault & create fresh identity)
export const resetVault = async (data: {
  clientSalt: string;
  vaultBlob: string;
  vaultSalt: string;
  actorToken?: string;
  username?: string;
}): Promise<ApiResponse<any>> => {
  const response = await axiosInstance.post<ApiResponse<any>>(
    '/api/auth/reset-vault',
    data,
  );
  return response.data;
};

// GET /api/auth/generate-pseudonym (fetch a guaranteed unique server-checked pseudonym)
export const fetchGeneratedPseudonym = async (): Promise<string | null> => {
  try {
    const response = await axiosInstance.get<ApiResponse<{ username: string }>>(
      '/api/auth/generate-pseudonym',
    );
    return response.data?.data?.username || null;
  } catch {
    return null;
  }
};
