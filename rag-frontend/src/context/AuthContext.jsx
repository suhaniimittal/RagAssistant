import { createContext, useContext, useEffect, useState } from "react";
import { fetchCurrentUser, loginUser, logoutUser, registerUser } from "../api/authApi";

// This is the global "is someone logged in" state the rest of the app reads,
// instead of every page separately calling the backend and tracking its own
// copy of "am I logged in" -- one shared source of truth.
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);

  // "checking" is true only during the very first load, while we ask the
  // backend "is there a valid session cookie already?" -- this is the
  // silent re-check that makes staying logged in survive a page refresh.
  // We show nothing (or a spinner) instead of the login page during this
  // brief window, so a logged-in user doesn't flash the login screen
  // before we've had a chance to confirm they're still authenticated.
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    fetchCurrentUser()
      .then((profile) => setUser(profile)) // profile is null if not logged in
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, []);

  async function login(identifier, password) {
    await loginUser(identifier, password); // sets the httpOnly cookie
    const profile = await fetchCurrentUser(); // now fetch who we just became
    setUser(profile);
  }

  async function register(identifier, password, confirmPassword) {
    // Per the earlier decision: register does NOT auto-login -- the caller
    // (Register.jsx) redirects to /login afterward instead.
    await registerUser(identifier, password, confirmPassword);
  }

  async function logout() {
    await logoutUser();
    setUser(null);
  }

  const value = {
    user,
    isLoggedIn: user !== null,
    checking,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside an <AuthProvider>");
  }
  return context;
}
