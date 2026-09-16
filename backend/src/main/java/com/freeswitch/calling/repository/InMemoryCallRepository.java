package com.freeswitch.calling.repository;

import com.freeswitch.calling.model.Call;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link CallRepository} backed by a
 * {@link ConcurrentHashMap}, keyed by call ID.
 *
 * <p>This is the application's live call-status store - what
 * {@code GET /api/v1/voice/calls/{callId}} reads while a call is in
 * progress. Calls exist here for the lifetime of the JVM only and vanish on
 * restart, which is fine for in-progress status: once a call reaches a
 * terminal state, {@link PersistentCallRepository} separately archives it as
 * a permanent row in the pre-existing {@code cdr} table (a call-detail
 * record is a historical artifact, not something this API serves back).
 */
@Repository
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
