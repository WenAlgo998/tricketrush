import type { ActiveSeatHold, Order } from "../api/purchase";
import type { Seat } from "../api/events";
import { formatPrice, formatRemainingTime } from "../formatters";

export type HeldSeat = ActiveSeatHold & {
  seat: Seat;
};

type HoldCheckoutPanelProps = {
  heldSeats: HeldSeat[];
  order: Order | null;
  now: number;
  isCheckingOut: boolean;
  actionError: string | null;
  onRelease: (hold: HeldSeat) => void;
  onCheckout: () => void;
};

export function HoldCheckoutPanel({
  heldSeats,
  order,
  now,
  isCheckingOut,
  actionError,
  onRelease,
  onCheckout
}: HoldCheckoutPanelProps) {
  const totalCents = heldSeats.reduce((total, hold) => total + hold.seat.priceCents, 0);
  const currency = heldSeats[0]?.seat.currency ?? "USD";

  return (
    <aside className="hold-panel" aria-labelledby="hold-panel-heading">
      <p className="eyebrow">Your selection</p>
      <h2 id="hold-panel-heading">{order === null ? "Hold seats before checkout." : "Order status"}</h2>

      {order !== null && <OrderStatus order={order} />}

      {order === null && heldSeats.length === 0 && (
        <p className="hold-panel-empty">Choose an available seat to place a five-minute hold.</p>
      )}

      {order === null && heldSeats.length > 0 && (
        <>
          <ul className="held-seat-list">
            {heldSeats.map((hold) => (
              <li key={hold.holdId}>
                <div>
                  <strong>Section {hold.seat.section}, row {hold.seat.row}, seat {hold.seat.seatNumber}</strong>
                  <span>{formatPrice(hold.seat.priceCents, hold.seat.currency)} · {formatRemainingTime(hold.expiresAt, now)} remaining</span>
                </div>
                <button className="link-button hold-release" type="button" onClick={() => onRelease(hold)}>
                  Release
                </button>
              </li>
            ))}
          </ul>
          <div className="hold-total">
            <span>Total</span>
            <strong>{formatPrice(totalCents, currency)}</strong>
          </div>
          <button className="button hold-checkout" type="button" disabled={isCheckingOut} onClick={onCheckout}>
            {isCheckingOut ? "Submitting checkout…" : `Checkout ${heldSeats.length} ${heldSeats.length === 1 ? "seat" : "seats"}`}
          </button>
          <p className="hold-panel-note">Checkout uses a request key that safely supports retrying the same submission.</p>
        </>
      )}

      {actionError !== null && <p className="form-error hold-error" role="alert">{actionError}</p>}
    </aside>
  );
}

function OrderStatus({ order }: { order: Order }) {
  if (order.status === "PENDING") {
    return <p className="order-status order-status--pending">Payment is processing. This page will update automatically.</p>;
  }
  if (order.status === "CONFIRMED") {
    return <p className="order-status order-status--confirmed">Your order is confirmed. Your seats are secured.</p>;
  }
  return <p className="order-status order-status--failed">Payment was not completed. The associated seats have been released.</p>;
}
