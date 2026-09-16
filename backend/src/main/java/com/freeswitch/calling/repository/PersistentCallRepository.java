package com.freeswitch.calling.repository;

import com.freeswitch.calling.entity.CallEntity;
import com.freeswitch.calling.model.Call;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link CallRepository} backed by PostgreSQL via Spring Data JPA - the
 * application's active call-storage implementation, replacing the original
 * in-memory one so call history survives a restart and can be queried
 * directly from the database.
 *
 * <p>Converts between {@link Call} (the mutable domain object the rest of
 * the application works with) and {@link CallEntity} (the JPA mapping) on
 * every read and write. Unlike the in-memory repository it replaces, this
 * repository does not share object references with its callers: each
 * {@link #findById} returns a freshly reconstructed {@link Call}, so every
 * caller that mutates a call's state (see
 * {@link com.freeswitch.calling.freeswitch.FreeSwitchEventListener}) must
 * call {@link #save(Call)} again afterward to persist that change.
 */
@Repository
public class PersistentCallRepository implements CallRepository {

    private final CallJpaRepository jpaRepository;

    public PersistentCallRepository(CallJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Call save(Call call) {
        jpaRepository.save(toEntity(call));
        return call;
    }

    @Override
    public Optional<Call> findById(String callId) {
        return jpaRepository.findById(callId).map(this::toDomain);
    }

    private CallEntity toEntity(Call call) {
        return new CallEntity(
                call.getCallId(),
                call.getFrom(),
                call.getTo(),
                call.getDirection(),
                call.getStatus(),
                call.getCreatedAt(),
                call.getAnsweredAt(),
                call.getCompletedAt());
    }

    private Call toDomain(CallEntity entity) {
        return new Call(
                entity.getCallId(),
                entity.getFromExtension(),
                entity.getToExtension(),
                entity.getDirection(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getAnsweredAt(),
                entity.getCompletedAt());
    }
}
