package com.ticketrush.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    void record(String aggregateType, UUID aggregateId, String action, Map<String, ?> details) {
        try {
            auditLogRepository.append(aggregateType, aggregateId, action, objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit log details", exception);
        }
    }
}
