package com.freeswitch.calling.repository;

import com.freeswitch.calling.model.Call;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link CallRepository} backed by a
 * {@link ConcurrentHashMap}, keyed by call ID.
 *
 * <p>Calls are stored for the lifetime of the JVM only and vanish on
 * restart. This was the original Step 1 implementation; it has since been
 * replaced as the application's active {@link CallRepository} bean by
 * {@link PersistentCallRepository}, which persists calls to PostgreSQL.
 *
 * <p>Deliberately <em>not</em> annotated {@code @Repository} any more, so it
 * is no longer picked up by Spring's component scan (avoiding an ambiguous
 * bean alongside {@link PersistentCallRepository}). It is kept only because
 * {@code VoiceCallServiceTest} constructs it directly for fast, dependency-free
 * unit tests of {@link com.freeswitch.calling.service.VoiceCallService}; it
 * also remains available as a manually-wired fallback for local use without a
 * database.
 */
public class InMemoryCallRepository implements CallRepository {

    private final Map<String, Call> calls = new ConcurrentHashMap<>();

    @Override
    public Call save(Call call) {
        calls.put(call.getCallId(), call);
        return call;
    }

    @Override
    public Optional<Call> findById(String callId) {
        return Optional.ofNullable(calls.get(callId));
    }
}
