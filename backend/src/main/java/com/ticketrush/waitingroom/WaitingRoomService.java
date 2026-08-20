package com.ticketrush.waitingroom;

import com.ticketrush.events.EventCatalogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class WaitingRoomService {

    private static final DefaultRedisScript<Long> JOIN_SCRIPT = new DefaultRedisScript<>("""
            if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
              local sequence = redis.call('INCR', KEYS[2])
              redis.call('ZADD', KEYS[1], sequence, ARGV[1])
            end
            return redis.call('ZRANK', KEYS[1], ARGV[1])
            """, Long.class);

    private static final DefaultRedisScript<Long> STATUS_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
            local existingToken = redis.call('GET', KEYS[3])
            if existingToken then
              return 1
            end
            local rank = redis.call('ZRANK', KEYS[1], ARGV[2])
            if not rank then
              return 0
            end
            local activeAdmissions = redis.call('ZCARD', KEYS[2])
            if activeAdmissions >= tonumber(ARGV[3]) or rank > 0 then
              return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[1] + tonumber(ARGV[4]), ARGV[2])
            redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[4])
            return 1
            """, Long.class);

    private final EventCatalogService eventCatalogService;
    private final StringRedisTemplate redisTemplate;
    private final WaitingRoomProperties properties;
    private final Clock clock;

    public WaitingRoomService(
            EventCatalogService eventCatalogService,
            StringRedisTemplate redisTemplate,
            WaitingRoomProperties properties,
            Clock clock
    ) {
        this.eventCatalogService = eventCatalogService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public QueueEntry join(UUID eventId, UUID buyerId) {
        eventCatalogService.findById(eventId);
        if (!properties.enabled()) {
            return new QueueEntry(1, 0);
        }
        try {
            Long rank = redisTemplate.execute(
                    JOIN_SCRIPT,
                    List.of(queueKey(eventId), "waiting-room:sequence"),
                    buyerId.toString()
            );
            if (rank == null || rank < 0) {
                throw new IllegalStateException("Unexpected waiting-room join response");
            }
            int position = Math.toIntExact(rank + 1);
            return new QueueEntry(position, estimateWaitSeconds(position));
        } catch (RuntimeException exception) {
            throw new WaitingRoomUnavailableException(exception);
        }
    }

    public QueueStatus status(UUID eventId, UUID buyerId) {
        eventCatalogService.findById(eventId);
        if (!properties.enabled()) {
            return new QueueStatus(true, "waiting-room-disabled");
        }
        String admissionKey = admissionKey(eventId, buyerId);
        try {
            Long admitted = redisTemplate.execute(
                    STATUS_SCRIPT,
                    List.of(queueKey(eventId), activeAdmissionsKey(eventId), admissionKey),
                    Long.toString(clock.millis()),
                    buyerId.toString(),
                    Integer.toString(properties.maxActiveAdmissions()),
                    Long.toString(properties.admissionTokenTtl().toMillis()),
                    UUID.randomUUID().toString()
            );
            if (admitted == null || (admitted != 0 && admitted != 1)) {
                throw new IllegalStateException("Unexpected waiting-room status response");
            }
            if (admitted == 0) {
                return new QueueStatus(false, null);
            }
            String token = redisTemplate.opsForValue().get(admissionKey);
            if (token == null) {
                throw new IllegalStateException("Waiting-room admission token was not available");
            }
            return new QueueStatus(true, token);
        } catch (RuntimeException exception) {
            throw new WaitingRoomUnavailableException(exception);
        }
    }

    private long estimateWaitSeconds(int position) {
        int peopleAheadOfAdmission = Math.max(0, position - properties.maxActiveAdmissions());
        long intervalSeconds = Math.max(1, properties.estimatedAdmissionInterval().toSeconds());
        return (long) Math.ceil((double) peopleAheadOfAdmission / properties.maxActiveAdmissions()) * intervalSeconds;
    }

    private static String queueKey(UUID eventId) {
        return "waiting-room:queue:" + eventId;
    }

    private static String activeAdmissionsKey(UUID eventId) {
        return "waiting-room:admissions:" + eventId;
    }

    private static String admissionKey(UUID eventId, UUID buyerId) {
        return "waiting-room:admission:" + eventId + ":" + buyerId;
    }

    public record QueueEntry(int position, long estimatedWaitSeconds) {
    }

    public record QueueStatus(boolean admitted, String token) {
    }
}
