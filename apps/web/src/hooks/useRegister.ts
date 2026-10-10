import { useState } from "react";
import { registerCitizen } from "../api/authService";
import { parseError } from "../utils/error-handler";

export interface RegisterFormData {
  email: string;
  password: string;
  confirmPassword: string;
  pincode: string;
}

export const useRegister = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleRegister = async (formData: RegisterFormData) => {
    setError(null);
    setSuccess(null);

    // Frontend validation
    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    if (formData.pincode.length !== 6) {
      setError("Pincode must be 6 digits");
      return;
    }

    setLoading(true);
    try {
      const response = await registerCitizen({
        email: formData.email,
        password: formData.password,
        pincode: formData.pincode,
      });

      if (response.success) {
        setSuccess(
          response.error ||
            response.message ||
            "A verification link has been sent to your email address."
        );
      } else {
        setError(response.error || response.message || "Registration failed");
      }
    } catch (err: any) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  return { handleRegister, loading, error, success };
};
