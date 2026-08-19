// Mirrors the backend's IdentifierValidator.java exactly, so the user
// gets the same "is this allowed?" answer instantly in the browser,
// instead of waiting for a round trip to Spring Boot to find out.

const EMAIL_PATTERN = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/;

/** Identifier can be EITHER a valid email OR a valid username. */
export function isValidIdentifier(identifier) {
  if (!identifier || identifier.trim() === "") return false;
  if (identifier.includes("@")) {
    return EMAIL_PATTERN.test(identifier);
  }
  return USERNAME_PATTERN.test(identifier);
}

export function identifierErrorMessage() {
  return "Enter a valid email, or a username of 3-20 letters/numbers/underscores";
}

export function isValidPassword(password) {
  return typeof password === "string" && password.length >= 8;
}

export function passwordErrorMessage() {
  return "Password must be at least 8 characters";
}

export function passwordsMatch(password, confirmPassword) {
  return password === confirmPassword;
}
