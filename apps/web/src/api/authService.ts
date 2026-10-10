import axiosInstance from "./axiosConfig";
import type { RegisterRequest, AuthRequest, ApiResponse, AuthResponse } from "../types/auth.types";

// POST /api/auth/register/citizen
export const registerCitizen = async (
  data: RegisterRequest
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    "/api/auth/register/citizen",
    data
  );
  return response.data;
};

// POST /api/auth/register/department
export const registerDepartment = async (
  data: RegisterRequest
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    "/api/auth/register/department",
    data
  );
  return response.data;
};

// POST /api/auth/login
export const loginUser = async (
  data: AuthRequest
): Promise<ApiResponse<AuthResponse>> => {
  const response = await axiosInstance.post<ApiResponse<AuthResponse>>(
    "/api/auth/login",
    data
  );
  return response.data;
};

// GET /api/auth/verify-email
export const verifyEmail = async (
  token: string
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.get<ApiResponse<string>>(
    `/api/auth/verify-email?token=${encodeURIComponent(token)}`
  );
  return response.data;
};

// POST /api/auth/resend-verification
export const resendVerification = async (
  email: string
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    `/api/auth/resend-verification?email=${encodeURIComponent(email.trim())}`
  );
  return response.data;
};

// POST /api/auth/forgot-password
export const forgotPassword = async (
  email: string
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    "/api/auth/forgot-password",
    { email: email.trim().toLowerCase() }
  );
  return response.data;
};

// POST /api/auth/reset-password
export const resetPassword = async (
  data: { token: string; newPassword: string }
): Promise<ApiResponse<string>> => {
  const response = await axiosInstance.post<ApiResponse<string>>(
    "/api/auth/reset-password",
    data
  );
  return response.data;
};