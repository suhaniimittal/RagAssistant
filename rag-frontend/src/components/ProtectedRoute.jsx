import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Wraps any page that requires login. If AuthContext is still checking
// (the silent /api/me call on page load hasn't resolved yet), we wait
// instead of redirecting -- otherwise a logged-in user would get bounced
// to /login for a split second on every refresh, before their cookie
// even had a chance to be validated.
export default function ProtectedRoute({ children }) {
  const { isLoggedIn, checking } = useAuth();

  if (checking) {
    return <div className="auth-checking">Checking session...</div>;
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }

  return children;
}
