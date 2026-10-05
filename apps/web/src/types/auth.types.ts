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
  serverActorToken?: string;
  vaultBlob?: string | null;
  vaultSalt?: string | null;
  hasVault?: boolean | null;
  seedBlindSalt?: string | null;
  user?: any;
}

export interface VaultBlobRequest {
  vaultBlob: string;
  vaultSalt?: string;
  actorToken?: string;
  clientSalt?: string;
  username?: string;
}

export interface VaultSetupRequest {
  clientSalt: string;
  vaultBlob: string;
  vaultSalt: string;
  actorToken?: string;
  username?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  error?: string;
  data?: T;
}
