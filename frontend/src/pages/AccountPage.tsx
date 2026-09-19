import { useAuth } from "../auth/AuthContext";

export function AccountPage() {
  const { session } = useAuth();

  if (session === null) {
    return null;
  }

  const expiresAt = new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(session.expiresAt));

  return (
    <section className="content-page narrow" aria-labelledby="account-heading">
      <p className="eyebrow">Account</p>
      <h1 id="account-heading">You’re signed in.</h1>
      <p className="page-intro">Your TicketRush session is ready for upcoming event discovery and seat selection.</p>
      <dl className="account-summary">
        <div>
          <dt>Email</dt>
          <dd>{session.email}</dd>
        </div>
        <div>
          <dt>Session expires</dt>
          <dd>{expiresAt}</dd>
        </div>
      </dl>
    </section>
  );
}
