package com.freeswitch.calling.repository;

import com.freeswitch.calling.model.Call;

import java.util.Optional;

/**
 * Storage abstraction for tracked calls.
 *
 * <p>Backed by an in-memory store for this step; the controller and service
 * layers depend only on this interface so a persistent implementation
 * (e.g. PostgreSQL) can replace {@link InMemoryCallRepository} later without
 * any change above this package.
 */
public interface CallRepository {

    Call save(Call call);

    Optional<Call> findById(String callId);
}
