package com.dreamreel.api.dramaforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DramaForgeEventHub {

    private static final Logger log = LoggerFactory.getLogger(DramaForgeEventHub.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<Subscription>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID projectId) {
        var emitter = new SseEmitter(SSE_TIMEOUT_MS);
        var sub = new Subscription(emitter);
        emitters.computeIfAbsent(projectId, key -> new CopyOnWriteArrayList<>()).add(sub);

        emitter.onCompletion(() -> {
            sub.markClosed();
            removeSubscription(projectId, sub);
        });
        emitter.onTimeout(() -> {
            sub.closeQuietly();
            removeSubscription(projectId, sub);
        });
        emitter.onError(ex -> {
            log.debug("SSE disconnected for project {}: {}", projectId, ex.getMessage());
            sub.markClosed();
            removeSubscription(projectId, sub);
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException ex) {
            log.debug("SSE subscribe failed for project {}: {}", projectId, ex.getMessage());
            sub.closeQuietly();
            removeSubscription(projectId, sub);
        }
        return emitter;
    }

    /** 心跳：避免空闲 SSE 被中间层掐断 */
    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (var entry : List.copyOf(emitters.entrySet())) {
            publish(entry.getKey(), "ping", Map.of("ts", System.currentTimeMillis()));
        }
    }

    public void publish(UUID projectId, String event, Object data) {
        var listeners = emitters.get(projectId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (var sub : List.copyOf(listeners)) {
            if (!sub.isOpen()) {
                removeSubscription(projectId, sub);
                continue;
            }
            try {
                sub.emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException ex) {
                log.debug("SSE publish skipped for project {} (client gone): {}", projectId, ex.getMessage());
                sub.closeQuietly();
                removeSubscription(projectId, sub);
            }
        }
    }

    private void removeSubscription(UUID projectId, Subscription sub) {
        sub.markClosed();
        var listeners = emitters.get(projectId);
        if (listeners != null) {
            listeners.remove(sub);
            if (listeners.isEmpty()) {
                emitters.remove(projectId);
            }
        }
    }

    private static final class Subscription {
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private boolean isOpen() {
            return !closed.get();
        }

        private void markClosed() {
            closed.set(true);
        }

        /** 主动结束 SSE，避免 async 上下文在断连后继续 flush 刷 ERROR 日志 */
        private void closeQuietly() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed / client gone
            }
        }
    }
}
