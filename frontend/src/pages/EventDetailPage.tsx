import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { type CatalogEvent, getEvent, getSeats, type Seat } from "../api/events";
import { ApiError } from "../api/http";
import { SeatMap } from "../components/SeatMap";
import { StatusPill } from "../components/StatusPill";
import { formatDateTime } from "../formatters";

type EventData = {
  event: CatalogEvent;
  seats: Seat[];
};

export function EventDetailPage() {
  const { eventId } = useParams();
  const [data, setData] = useState<EventData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    if (eventId === undefined) {
      setData(null);
      setError("This event could not be found.");
      setIsLoading(false);
      return undefined;
    }

    let cancelled = false;
    setIsLoading(true);
    setError(null);
    setData(null);

    Promise.all([getEvent(eventId), getSeats(eventId)])
      .then(([event, seats]) => {
        if (!cancelled) {
          setData({ event, seats });
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiError ? cause.message : "We could not load this event. Please try again.");
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
  }, [eventId, requestVersion]);

  return (
    <section className="content-page event-detail" aria-labelledby="event-heading">
      <Link className="back-link" to="/events">← All events</Link>
      {isLoading && <div className="detail-skeleton" aria-label="Loading event" aria-busy="true" />}
      {!isLoading && error !== null && (
        <div className="request-state" role="alert">
          <p>{error}</p>
          <button className="button button-secondary" type="button" onClick={() => setRequestVersion((version) => version + 1)}>
            Try again
          </button>
        </div>
      )}
      {!isLoading && data !== null && (
        <>
          <header className="event-detail-header">
            <div>
              <p className="eyebrow">{data.event.venueName}</p>
              <h1 id="event-heading">{data.event.name}</h1>
              <p className="event-date">{formatDateTime(data.event.eventStartAt)}</p>
            </div>
            <StatusPill status={data.event.status} />
          </header>
          <SeatMap seats={data.seats} />
        </>
      )}
    </section>
  );
}
