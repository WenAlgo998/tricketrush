package com.ticketrush.events;

import java.util.List;

public record EventCatalogPage(List<EventCatalogItem> content, long totalElements) {
}
