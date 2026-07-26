package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeatMapService {

    private final EventCatalogService eventCatalogService;
    private final SeatMapRepository seatMapRepository;

    SeatMapService(EventCatalogService eventCatalogService, SeatMapRepository seatMapRepository) {
        this.eventCatalogService = eventCatalogService;
        this.seatMapRepository = seatMapRepository;
    }

    @Transactional(readOnly = true)
    public List<SeatMapItem> findByEventId(UUID eventId) {
        eventCatalogService.findById(eventId);
        return seatMapRepository.findByEventId(eventId);
    }
}
