package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventCatalogService {

    private final EventCatalogRepository eventCatalogRepository;

    EventCatalogService(EventCatalogRepository eventCatalogRepository) {
        this.eventCatalogRepository = eventCatalogRepository;
    }

    @Transactional(readOnly = true)
    public EventCatalogPage findPage(int page, int size) {
        return eventCatalogRepository.findPage(size, Math.multiplyExact(page, size));
    }

    @Transactional(readOnly = true)
    public EventCatalogItem findById(UUID eventId) {
        return eventCatalogRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
    }
}
