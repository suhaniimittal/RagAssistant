import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  isValidIdentifier,
  identifierErrorMessage,
  isValidPassword,
  passwordErrorMessage,
  passwordsMatch,
} from "../utils/validators";

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [serverError, setServerError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Confirm-password's validity depends on the password field too, so it
  // takes both values rather than just its own.
  function validateField(field, values) {
    if (field === "identifier" && !isValidIdentifier(values.identifier)) {
      return identifierErrorMessage();
    }
    if (field === "password" && !isValidPassword(values.password)) {
      return passwordErrorMessage();
    }
    if (
      field === "confirmPassword" &&
      !passwordsMatch(values.password, values.confirmPassword)
    ) {
      return "Passwords do not match";
    }
    return "";
  }

  function handleBlur(field) {
    setTouched((t) => ({ ...t, [field]: true }));
    setErrors((e) => ({
      ...e,
      [field]: validateField(field, { identifier, password, confirmPassword }),
    }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setServerError("");
    setSuccessMessage("");

    const values = { identifier, password, confirmPassword };
    const nextErrors = {
      identifier: validateField("identifier", values),
      password: validateField("password", values),
      confirmPassword: validateField("confirmPassword", values),
    };
    setErrors(nextErrors);
    setTouched({ identifier: true, password: true, confirmPassword: true });

    if (nextErrors.identifier || nextErrors.password || nextErrors.confirmPassword) {
      return;
    }

    setSubmitting(true);
    try {
      await register(identifier, password, confirmPassword);
      // Per the earlier decision: redirect to the login page rather than
      // auto-logging in, so register and login stay fully separate flows.
      setSuccessMessage("Registration successful. Redirecting to login...");
      setTimeout(() => navigate("/login"), 1200);
    } catch (err) {
      setServerError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <h1>Register</h1>

        <label htmlFor="identifier">Username or email</label>
        <input
          id="identifier"
          type="text"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          onBlur={() => handleBlur("identifier")}
        />
        {touched.identifier && errors.identifier && (
          <p className="field-error">{errors.identifier}</p>
        )}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onBlur={() => handleBlur("password")}
        />
        {touched.password && errors.password && (
          <p className="field-error">{errors.password}</p>
        )}

        <label htmlFor="confirmPassword">Confirm password</label>
        <input
          id="confirmPassword"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          onBlur={() => handleBlur("confirmPassword")}
        />
        {touched.confirmPassword && errors.confirmPassword && (
          <p className="field-error">{errors.confirmPassword}</p>
        )}

        {serverError && <p className="server-error">{serverError}</p>}
        {successMessage && <p className="success-message">{successMessage}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? "Registering..." : "Register"}
        </button>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  );
}
