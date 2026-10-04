import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { type CatalogEvent, getEvent, getSeats, type Seat } from "../api/events";
import { ApiError } from "../api/http";
import {
  checkout,
  createSeatHold,
  getActiveSeatHolds,
  getOrder,
  releaseSeatHold,
  type ActiveSeatHold,
  type Order
} from "../api/purchase";
import { useAuth } from "../auth/AuthContext";
import { HoldCheckoutPanel, type HeldSeat } from "../components/HoldCheckoutPanel";
import { SeatMap } from "../components/SeatMap";
import { StatusPill } from "../components/StatusPill";
import { formatDateTime } from "../formatters";
import { useSeatStatusSubscription, type SeatStatusUpdate } from "../live/useSeatStatusSubscription";

type EventData = {
  event: CatalogEvent;
  seats: Seat[];
};

type CheckoutAttempt = {
  idempotencyKey: string;
  holdIds: string[];
};

export function EventDetailPage() {
  const { eventId } = useParams();
  const { session } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState<EventData | null>(null);
  const [holds, setHolds] = useState<ActiveSeatHold[]>([]);
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [pendingSeatId, setPendingSeatId] = useState<string | null>(null);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [checkoutAttempt, setCheckoutAttempt] = useState<CheckoutAttempt | null>(null);
  const [requestVersion, setRequestVersion] = useState(0);
  const now = useNow();

  const refreshSeats = useCallback(async () => {
    if (eventId === undefined) {
      return;
    }
    const seats = await getSeats(eventId);
    setData((current) => current === null ? null : { ...current, seats });
  }, [eventId]);

  const refreshHolds = useCallback(async () => {
    if (eventId === undefined || session === null) {
      setHolds([]);
      return;
    }
    setHolds(await getActiveSeatHolds(eventId, session.accessToken));
  }, [eventId, session]);

  const applySeatStatus = useCallback((update: SeatStatusUpdate) => {
    setData((current) => {
      if (current === null) {
        return null;
      }
      return {
        ...current,
        seats: current.seats.map((seat) => {
          if (seat.id !== update.seatId || update.version <= seat.version) {
            return seat;
          }
          return { ...seat, status: update.status, version: update.version };
        })
      };
    });
    if (update.status === "AVAILABLE") {
      setHolds((current) => current.filter((hold) => hold.seatId !== update.seatId));
    }
  }, []);

  const liveConnectionState = useSeatStatusSubscription({
    eventId,
    accessToken: session?.accessToken,
    onSeatStatus: applySeatStatus,
    onConnected: () => {
      void refreshSeats();
      void refreshHolds();
    }
  });

  useEffect(() => {
    if (eventId === undefined) {
      setData(null);
      setHolds([]);
      setError("This event could not be found.");
      setIsLoading(false);
      return undefined;
    }

    let cancelled = false;
    setIsLoading(true);
    setError(null);
    setActionError(null);
    setData(null);
    setHolds([]);
    setOrder(null);
    setCheckoutAttempt(null);

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

    if (session !== null) {
      getActiveSeatHolds(eventId, session.accessToken)
        .then((activeHolds) => {
          if (!cancelled) {
            setHolds(activeHolds);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setHolds([]);
          }
        });
    }

    return () => {
      cancelled = true;
    };
  }, [eventId, requestVersion, session]);

  useEffect(() => {
    const hasExpiredHold = holds.some((hold) => new Date(hold.expiresAt).getTime() <= now);
    if (hasExpiredHold) {
      setHolds((current) => current.filter((hold) => new Date(hold.expiresAt).getTime() > now));
      void refreshSeats();
    }
  }, [holds, now, refreshSeats]);

  useEffect(() => {
    if (order?.status !== "PENDING" || session === null) {
      return undefined;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const nextOrder = await getOrder(order.orderId, session.accessToken);
        if (!cancelled) {
          setOrder(nextOrder);
          if (nextOrder.status !== "PENDING") {
            void refreshSeats();
            void refreshHolds();
          }
        }
      } catch {
        // A later polling attempt or WebSocket reconciliation can still report the final status.
      }
    };

    void poll();
    const interval = window.setInterval(() => void poll(), 2_000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [order?.orderId, order?.status, refreshHolds, refreshSeats, session]);

  const activeHolds = useMemo(
    () => holds.filter((hold) => new Date(hold.expiresAt).getTime() > now),
    [holds, now]
  );
  const heldSeatIds = useMemo(() => new Set(activeHolds.map((hold) => hold.seatId)), [activeHolds]);
  const heldSeats = useMemo<HeldSeat[]>(() => {
    if (data === null) {
      return [];
    }
    return activeHolds.flatMap((hold) => {
      const seat = data.seats.find((candidate) => candidate.id === hold.seatId);
      return seat === undefined ? [] : [{ ...hold, seat }];
    });
  }, [activeHolds, data]);

  const updateSeat = useCallback((seatId: string, status: Seat["status"], version: number) => {
    setData((current) => {
      if (current === null) {
        return null;
      }
      return {
        ...current,
        seats: current.seats.map((seat) => seat.id === seatId ? { ...seat, status, version } : seat)
      };
    });
  }, []);

  async function handleSeatAction(seat: Seat) {
    if (session === null) {
      navigate("/login", { state: { from: location.pathname } });
      return;
    }

    const existingHold = activeHolds.find((hold) => hold.seatId === seat.id);
    setPendingSeatId(seat.id);
    setActionError(null);
    try {
      if (existingHold !== undefined) {
        await releaseSeatHold(existingHold.holdId, session.accessToken);
        setHolds((current) => current.filter((hold) => hold.holdId !== existingHold.holdId));
        setCheckoutAttempt(null);
        updateSeat(seat.id, "AVAILABLE", seat.version + 1);
      } else {
        const createdHold = await createSeatHold(eventId!, seat.id, seat.version, session.accessToken);
        setHolds((current) => [...current, { ...createdHold, seatId: seat.id }]);
        setCheckoutAttempt(null);
        updateSeat(seat.id, "HELD", seat.version + 1);
      }
    } catch (cause) {
      setActionError(cause instanceof ApiError ? cause.message : "We could not update this hold. Please try again.");
      void refreshSeats();
      void refreshHolds();
    } finally {
      setPendingSeatId(null);
    }
  }

  async function handleCheckout() {
    if (session === null || activeHolds.length === 0) {
      return;
    }

    const holdIds = activeHolds.map((hold) => hold.holdId);
    const attempt = checkoutAttempt !== null && sameIds(checkoutAttempt.holdIds, holdIds)
      ? checkoutAttempt
      : { idempotencyKey: crypto.randomUUID(), holdIds };
    setCheckoutAttempt(attempt);
    setActionError(null);
    setIsCheckingOut(true);

    try {
      const createdOrder = await checkout(attempt.holdIds, attempt.idempotencyKey, session.accessToken);
      setOrder(createdOrder);
      setHolds([]);
      setCheckoutAttempt(null);
    } catch (cause) {
      const message = cause instanceof ApiError ? cause.message : "We could not submit checkout. Please try again.";
      setActionError(message);
      if (cause instanceof ApiError && cause.code === "CHECKOUT_HOLD_CONFLICT") {
        setCheckoutAttempt(null);
        void refreshSeats();
        void refreshHolds();
      }
    } finally {
      setIsCheckingOut(false);
    }
  }

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
          {session === null ? (
            <section className="sign-in-to-hold" aria-labelledby="sign-in-to-hold-heading">
              <div>
                <p className="eyebrow">Ready to book?</p>
                <h2 id="sign-in-to-hold-heading">Sign in to hold seats.</h2>
                <p>Seat availability is public. Holding and checkout require a TicketRush account.</p>
              </div>
              <Link className="button" to="/login" state={{ from: location.pathname }}>Sign in to continue</Link>
            </section>
          ) : (
            <p className={`live-status live-status--${liveConnectionState}`} role="status">
              <span aria-hidden="true" /> {liveStatusMessage(liveConnectionState)}
            </p>
          )}
          <div className="booking-layout">
            <SeatMap
              seats={data.seats}
              heldSeatIds={heldSeatIds}
              pendingSeatId={pendingSeatId}
              onSeatAction={session === null ? undefined : handleSeatAction}
            />
            {session !== null && (
              <HoldCheckoutPanel
                heldSeats={heldSeats}
                order={order}
                now={now}
                isCheckingOut={isCheckingOut}
                actionError={actionError}
                onRelease={(hold) => void handleSeatAction(hold.seat)}
                onCheckout={() => void handleCheckout()}
              />
            )}
          </div>
        </>
      )}
    </section>
  );
}

function useNow() {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(interval);
  }, []);
  return now;
}

function sameIds(first: string[], second: string[]): boolean {
  return first.length === second.length && first.every((id) => second.includes(id));
}

function liveStatusMessage(state: ReturnType<typeof useSeatStatusSubscription>): string {
  if (state === "connected") {
    return "Live availability connected";
  }
  if (state === "reconnecting") {
    return "Reconnecting live availability";
  }
  return "Connecting live availability";
}
