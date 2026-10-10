import { Navigate } from "react-router-dom";
import { getAuthToken } from "../utils/auth";

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const token = getAuthToken();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
