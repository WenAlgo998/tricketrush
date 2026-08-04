package com.ticketrush.events.api;

import com.ticketrush.common.api.PageResponse;
import com.ticketrush.events.EventCatalogItem;
import com.ticketrush.events.EventCatalogPage;
import com.ticketrush.events.EventCatalogService;
import com.ticketrush.events.SeatMapItem;
import com.ticketrush.events.SeatMapService;
import com.ticketrush.events.ReservationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@Validated
public class EventController {

    private final EventCatalogService eventCatalogService;
    private final SeatMapService seatMapService;
    private final ReservationService reservationService;

    public EventController(
            EventCatalogService eventCatalogService,
            SeatMapService seatMapService,
            ReservationService reservationService
    ) {
        this.eventCatalogService = eventCatalogService;
        this.seatMapService = seatMapService;
        this.reservationService = reservationService;
    }

    @GetMapping
    public PageResponse<EventResponse> listEvents(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        EventCatalogPage result = eventCatalogService.findPage(page, size);
        return new PageResponse<>(
                result.content().stream().map(EventResponse::from).toList(),
                page,
                size,
                result.totalElements(),
                (int) Math.ceil((double) result.totalElements() / size)
        );
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable UUID eventId) {
        return EventResponse.from(eventCatalogService.findById(eventId));
    }

    @GetMapping("/{eventId}/seats")
    public List<SeatResponse> getSeatMap(@PathVariable UUID eventId) {
        return seatMapService.findByEventId(eventId).stream().map(SeatResponse::from).toList();
    }

    @PostMapping("/{eventId}/seats/{seatId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveSeat(
            @PathVariable UUID eventId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ReservationService.ConfirmedReservation reservation = reservationService.reserve(
                eventId, seatId, UUID.fromString(jwt.getSubject())
        );
        return new ReservationResponse(reservation.orderId(), "CONFIRMED");
    }

    public record EventResponse(
            UUID id,
            String name,
            String venueName,
            java.time.OffsetDateTime saleStartAt,
            java.time.OffsetDateTime eventStartAt,
            String status
    ) {
        private static EventResponse from(EventCatalogItem event) {
            return new EventResponse(
                    event.id(),
                    event.name(),
                    event.venueName(),
                    event.saleStartAt(),
                    event.eventStartAt(),
                    event.status().name()
            );
        }
    }

    public record SeatResponse(
            UUID id,
            String section,
            String row,
            String seatNumber,
            int priceCents,
            String currency,
            String status,
            int version
    ) {
        private static SeatResponse from(SeatMapItem seat) {
            return new SeatResponse(
                    seat.id(),
                    seat.section(),
                    seat.row(),
                    seat.seatNumber(),
                    seat.priceCents(),
                    seat.currency(),
                    seat.status().name(),
                    seat.version()
            );
        }
    }

    public record ReservationResponse(UUID orderId, String status) {
    }
}
