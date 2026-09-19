import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function LandingPage() {
  const { isAuthenticated } = useAuth();

  return (
    <section className="hero" aria-labelledby="hero-heading">
      <p className="eyebrow">A dependable path to your next event</p>
      <h1 id="hero-heading">Find the seat. Keep your place.</h1>
      <p className="hero-copy">
        TicketRush is built for fair, reliable ticket access when demand is high.
      </p>
      <div className="hero-actions">
        <Link className="button" to={isAuthenticated ? "/account" : "/register"}>
          {isAuthenticated ? "View account" : "Create account"}
        </Link>
        {!isAuthenticated && <Link className="text-link" to="/login">Already have an account? Sign in</Link>}
      </div>
      <div className="trust-grid" aria-label="TicketRush product principles">
        <article>
          <strong>Fair access</strong>
          <span>Demand spikes are managed before they become a checkout problem.</span>
        </article>
        <article>
          <strong>Reliable holds</strong>
          <span>Availability is always confirmed by the service, not the browser.</span>
        </article>
        <article>
          <strong>Clear status</strong>
          <span>Every purchase flow has a traceable outcome.</span>
        </article>
      </div>
    </section>
  );
}
