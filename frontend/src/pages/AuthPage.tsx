import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../api/http";
import { useAuth } from "../auth/AuthContext";

type AuthMode = "login" | "register";

export function AuthPage({ mode }: { mode: AuthMode }) {
  const { isAuthenticated, login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isRegistration = mode === "register";

  if (isAuthenticated) {
    return <Navigate to="/account" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const normalizedEmail = email.trim().toLowerCase();
    if (!/^\S+@\S+\.\S+$/.test(normalizedEmail)) {
      setError("Enter a valid email address.");
      return;
    }
    if (password.length < 8 || password.length > 72) {
      setError("Your password must be between 8 and 72 characters.");
      return;
    }

    setIsSubmitting(true);
    try {
      const authenticate = isRegistration ? register : login;
      await authenticate({ email: normalizedEmail, password });
      const destination = (location.state as { from?: string } | null)?.from ?? "/account";
      navigate(destination, { replace: true });
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "We could not sign you in. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-page" aria-labelledby="auth-heading">
      <div className="auth-panel">
        <p className="eyebrow">{isRegistration ? "Create your account" : "Welcome back"}</p>
        <h1 id="auth-heading">{isRegistration ? "Ready when the tickets are." : "Sign in to TicketRush."}</h1>
        <p className="page-intro">
          {isRegistration
            ? "Create an account to keep your reservation and checkout activity tied to you."
            : "Use the email and password associated with your TicketRush account."}
        </p>
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-describedby={error !== null ? "auth-error" : undefined}
            required
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete={isRegistration ? "new-password" : "current-password"}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-describedby={error !== null ? "auth-error" : undefined}
            required
          />
          <p className="field-help">Use 8–72 characters.</p>
          {error !== null && <p className="form-error" id="auth-error" role="alert">{error}</p>}
          <button className="button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Please wait…" : isRegistration ? "Create account" : "Sign in"}
          </button>
        </form>
        <p className="auth-switch">
          {isRegistration ? "Already have an account?" : "New to TicketRush?"} {" "}
          <Link to={isRegistration ? "/login" : "/register"}>
            {isRegistration ? "Sign in" : "Create one"}
          </Link>
        </p>
      </div>
    </section>
  );
}
