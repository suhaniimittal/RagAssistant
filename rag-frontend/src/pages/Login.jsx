import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { isValidIdentifier, identifierErrorMessage } from "../utils/validators";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");

  // Per-field errors, only shown once a field has been touched (onBlur) --
  // matches the "validate on blur + on submit" UX decided earlier, so
  // people aren't shown red errors before they've even typed anything.
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [serverError, setServerError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function validateField(field, value) {
    if (field === "identifier" && !isValidIdentifier(value)) {
      return identifierErrorMessage();
    }
    if (field === "password" && value.trim() === "") {
      return "Password is required";
    }
    return "";
  }

  function handleBlur(field, value) {
    setTouched((t) => ({ ...t, [field]: true }));
    setErrors((e) => ({ ...e, [field]: validateField(field, value) }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setServerError("");

    const identifierError = validateField("identifier", identifier);
    const passwordError = validateField("password", password);
    setErrors({ identifier: identifierError, password: passwordError });
    setTouched({ identifier: true, password: true });

    if (identifierError || passwordError) {
      return; // stop here, don't call the backend with known-bad input
    }

    setSubmitting(true);
    try {
      await login(identifier, password);
      navigate("/dashboard");
    } catch (err) {
      // The backend deliberately returns one generic message whether the
      // identifier doesn't exist or the password is wrong -- we just
      // display whatever it sent, without adding our own guesses.
      setServerError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <h1>Log in</h1>

        <label htmlFor="identifier">Username or email</label>
        <input
          id="identifier"
          type="text"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          onBlur={(e) => handleBlur("identifier", e.target.value)}
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
          onBlur={(e) => handleBlur("password", e.target.value)}
        />
        {touched.password && errors.password && (
          <p className="field-error">{errors.password}</p>
        )}

        {serverError && <p className="server-error">{serverError}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? "Logging in..." : "Log in"}
        </button>

        <p className="auth-switch">
          Don't have an account? <Link to="/register">Register</Link>
        </p>
      </form>
    </div>
  );
}
