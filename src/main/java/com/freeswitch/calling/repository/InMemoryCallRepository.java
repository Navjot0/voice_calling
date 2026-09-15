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
 * <p>Calls are stored for the lifetime of the JVM only. This is sufficient
 * for Step 1 (call initiation and lifecycle tracking); a persistent store
 * can be swapped in later behind the same {@link CallRepository} interface.
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
