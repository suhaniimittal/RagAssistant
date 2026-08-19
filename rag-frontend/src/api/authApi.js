// All calls to the Spring Boot auth endpoints live here, separate from any
// UI component -- pages just call these functions and don't need to know
// anything about fetch(), headers, or how the cookie gets attached.
//
// API_BASE_URL (see ./config.js) is the one place that decides where the
// backend actually is: http://localhost:8080 while developing (frontend and
// backend run as two separate processes, talking over a real cross-origin
// request), or "" (same origin) once Spring Boot is serving the built
// frontend itself.

import { API_BASE_URL } from "./config";

const AUTH_BASE_URL = `${API_BASE_URL}/api/auth`;
const USER_BASE_URL = `${API_BASE_URL}/api/user`;

// credentials: "include" is required now that these can be genuine
// cross-origin requests (different port = different origin) -- without
// this, the browser won't attach the httpOnly auth cookie to the request,
// or accept a new one back from the response.
const baseOptions = {
  credentials: "include",
  headers: { "Content-Type": "application/json" },
};

export async function registerUser(identifier, password, confirmPassword) {
  const response = await fetch(`${AUTH_BASE_URL}/register`, {
    ...baseOptions,
    method: "POST",
    body: JSON.stringify({ identifier, password, confirmPassword }),
  });

  const message = await response.text();
  if (!response.ok) {
    throw new Error(message || "Registration failed");
  }
  return message;
}

export async function loginUser(identifier, password) {
  const response = await fetch(`${AUTH_BASE_URL}/login`, {
    ...baseOptions,
    method: "POST",
    body: JSON.stringify({ identifier, password }),
  });

  const message = await response.text();
  if (!response.ok) {
    throw new Error(message || "Login failed");
  }
  return message;
}

/**
 * Called on every app load/refresh. Returns the user's profile if the
 * httpOnly cookie is still valid, or null if not (never throws for the
 * "not logged in" case -- that's an expected outcome, not an error).
 */
export async function fetchCurrentUser() {
  // Served by UserController at GET /api/user
  // it's a user-profile lookup, not an auth action).
  const response = await fetch(USER_BASE_URL, {
    ...baseOptions,
    method: "GET",
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error("Failed to fetch current user");
  }
  return response.json(); // { id, identifier }
}

export async function logoutUser() {
  await fetch(`${AUTH_BASE_URL}/logout`, {
    ...baseOptions,
    method: "POST",
  });
}
