package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ReservationService {

    private final EventCatalogRepository eventCatalogRepository;
    private final ReservationRepository reservationRepository;
    private final SeatStatusEventPublisher seatStatusEventPublisher;
    private final Clock clock;

    ReservationService(
            EventCatalogRepository eventCatalogRepository,
            ReservationRepository reservationRepository,
            SeatStatusEventPublisher seatStatusEventPublisher,
            Clock clock
    ) {
        this.eventCatalogRepository = eventCatalogRepository;
        this.reservationRepository = reservationRepository;
        this.seatStatusEventPublisher = seatStatusEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ConfirmedReservation reserve(UUID eventId, UUID seatId, UUID userId) {
        EventCatalogItem event = eventCatalogRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        if (event.status() != EventStatus.ON_SALE || event.saleStartAt().toInstant().isAfter(clock.instant())) {
            throw new EventNotOnSaleException();
        }

        if (!reservationRepository.markSeatSoldIfAvailable(eventId, seatId)) {
            throw new SeatUnavailableException();
        }

        UUID orderId = UUID.randomUUID();
        reservationRepository.createConfirmedOrder(orderId, userId, eventId, seatId);
        seatStatusEventPublisher.publish(
                eventId, seatId, SeatStatus.SOLD, reservationRepository.findSeatVersion(seatId)
        );
        return new ConfirmedReservation(orderId);
    }

    public record ConfirmedReservation(UUID orderId) {
    }
}
