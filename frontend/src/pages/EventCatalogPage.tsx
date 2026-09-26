import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { type CatalogEvent, getEvents, type PageResponse } from "../api/events";
import { ApiError } from "../api/http";
import { StatusPill } from "../components/StatusPill";
import { formatDateTime } from "../formatters";

const pageSize = 12;

export function EventCatalogPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CatalogEvent> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    setResult(null);

    getEvents(page, pageSize)
      .then((response) => {
        if (!cancelled) {
          setResult(response);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiError ? cause.message : "We could not load events. Please try again.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [page, requestVersion]);

  return (
    <section className="content-page event-catalog" aria-labelledby="events-heading">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Events</p>
          <h1 id="events-heading">Make room for a good night.</h1>
        </div>
        {result !== null && <p className="result-count">{result.totalElements} events</p>}
      </div>

      {isLoading && <CatalogSkeleton />}
      {!isLoading && error !== null && (
        <div className="request-state" role="alert">
          <p>{error}</p>
          <button className="button button-secondary" type="button" onClick={() => setRequestVersion((version) => version + 1)}>
            Try again
          </button>
        </div>
      )}
      {!isLoading && error === null && result?.content.length === 0 && (
        <div className="empty-state">There are no events to show right now. Please check back soon.</div>
      )}
      {!isLoading && error === null && result !== null && result.content.length > 0 && (
        <>
          <div className="event-grid">
            {result.content.map((event) => <EventCard event={event} key={event.id} />)}
          </div>
          <nav className="pagination" aria-label="Event pages">
            <button
              className="button button-secondary"
              type="button"
              disabled={page === 0}
              onClick={() => setPage((currentPage) => currentPage - 1)}
            >
              Previous
            </button>
            <span>Page {result.page + 1} of {Math.max(result.totalPages, 1)}</span>
            <button
              className="button button-secondary"
              type="button"
              disabled={page + 1 >= result.totalPages}
              onClick={() => setPage((currentPage) => currentPage + 1)}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  );
}

function EventCard({ event }: { event: CatalogEvent }) {
  return (
    <article className="event-card">
      <div className="event-card-topline">
        <StatusPill status={event.status} />
        <span>{formatDateTime(event.eventStartAt)}</span>
      </div>
      <h2>{event.name}</h2>
      <p>{event.venueName}</p>
      <dl className="event-card-details">
        <div>
          <dt>Sale starts</dt>
          <dd>{formatDateTime(event.saleStartAt)}</dd>
        </div>
      </dl>
      <Link className="text-link event-card-link" to={`/events/${event.id}`}>
        View event and seats <span aria-hidden="true">→</span>
      </Link>
    </article>
  );
}

function CatalogSkeleton() {
  return (
    <div className="event-grid" aria-label="Loading events" aria-busy="true">
      {Array.from({ length: 6 }, (_, index) => <div className="event-card event-card--skeleton" key={index} />)}
    </div>
  );
}
