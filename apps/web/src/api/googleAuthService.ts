import axiosInstance from './axiosConfig';

// Phase 1: Check if new or existing user
export const checkGoogleUser = async (idToken: string) => {
  const response = await axiosInstance.post('/api/auth/google', {
    token: idToken,
  });
  return response.data; // { message: "onboarding_required" } OR { success: true, data: { token } }
};

// Phase 2: Create new account with onboarding data
export const registerWithGoogle = async (payload: {
  token: string;
  pincode: string;
  isAdult: boolean;
  acceptedPolicy: boolean;
}) => {
  const response = await axiosInstance.post('/api/auth/google', payload);
  return response.data;
};
