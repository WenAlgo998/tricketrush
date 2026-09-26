import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function AppLayout() {
  const { isAuthenticated, session, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="site-header">
        <Link className="brand" to="/" aria-label="TicketRush home">
          <span className="brand-mark" aria-hidden="true">T</span>
          <span>TicketRush</span>
        </Link>
        <nav className="site-nav" aria-label="Primary navigation">
          <NavLink to="/events">Events</NavLink>
          {isAuthenticated ? (
            <>
              <NavLink to="/account">Account</NavLink>
              <button className="link-button" type="button" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Sign in</NavLink>
              <NavLink className="button button-small" to="/register">Create account</NavLink>
            </>
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="site-footer">
        <span>TicketRush</span>
        {session !== null && <span>Signed in as {session.email}</span>}
      </footer>
    </div>
  );
}
