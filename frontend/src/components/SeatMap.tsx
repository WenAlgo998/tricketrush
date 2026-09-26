import type { Seat, SeatStatus } from "../api/events";
import { formatPrice, titleCase } from "../formatters";

type SeatMapProps = {
  seats: Seat[];
};

const statusOrder: SeatStatus[] = ["AVAILABLE", "HELD", "SOLD"];

export function SeatMap({ seats }: SeatMapProps) {
  const sections = groupSeats(seats);
  const counts = countByStatus(seats);

  return (
    <section className="seat-map" aria-labelledby="seat-map-heading">
      <div className="seat-map-heading">
        <div>
          <p className="eyebrow">Current availability</p>
          <h2 id="seat-map-heading">Choose your section.</h2>
        </div>
        <ul className="seat-legend" aria-label="Seat status legend">
          {statusOrder.map((status) => (
            <li key={status}>
              <span className={`seat-swatch seat-swatch--${status.toLowerCase()}`} aria-hidden="true" />
              {titleCase(status)} <strong>{counts[status]}</strong>
            </li>
          ))}
        </ul>
      </div>
      <p className="seat-map-note">Availability is read-only in this release. Seat holds and checkout are added next.</p>
      {seats.length === 0 ? (
        <div className="empty-state">This event does not have a published seat map yet.</div>
      ) : (
        <div className="seat-sections">
          {sections.map(([section, rows]) => (
            <section className="seat-section" key={section} aria-labelledby={`section-${section}`}>
              <h3 id={`section-${section}`}>Section {section}</h3>
              {rows.map(([row, rowSeats]) => (
                <div className="seat-row" key={row}>
                  <span className="seat-row-label">Row {row}</span>
                  <ul className="seat-row-seats" aria-label={`Section ${section}, row ${row}`}>
                    {rowSeats.map((seat) => (
                      <li key={seat.id}>
                        <span
                          className={`seat seat--${seat.status.toLowerCase()}`}
                          aria-label={`Section ${seat.section}, row ${seat.row}, seat ${seat.seatNumber}: ${titleCase(seat.status)}, ${formatPrice(seat.priceCents, seat.currency)}`}
                        >
                          {seat.seatNumber}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </section>
          ))}
        </div>
      )}
    </section>
  );
}

function groupSeats(seats: Seat[]): Array<[string, Array<[string, Seat[]]>]> {
  const sections = new Map<string, Map<string, Seat[]>>();

  for (const seat of seats) {
    const rows = sections.get(seat.section) ?? new Map<string, Seat[]>();
    const rowSeats = rows.get(seat.row) ?? [];
    rowSeats.push(seat);
    rows.set(seat.row, rowSeats);
    sections.set(seat.section, rows);
  }

  return Array.from(sections, ([section, rows]) => [section, Array.from(rows)]);
}

function countByStatus(seats: Seat[]): Record<SeatStatus, number> {
  const counts: Record<SeatStatus, number> = { AVAILABLE: 0, HELD: 0, SOLD: 0 };
  for (const seat of seats) {
    counts[seat.status] += 1;
  }
  return counts;
}
