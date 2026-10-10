
export interface RegisterRequest {
  email: string;
  password: string;
  pincode: string;
  username?: string;
}

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token?: string;
  authToken?: string;
  accessToken?: string;
  jwt?: string;
}


export interface ApiResponse<T> {
  success: boolean;
  message: string;
  error?: string;
  data?: T;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
