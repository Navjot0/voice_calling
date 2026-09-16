package com.freeswitch.calling.repository;

import com.freeswitch.calling.entity.CallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository backing {@link PersistentCallRepository}.
 *
 * <p>Not used directly outside this package - the rest of the application
 * depends only on the {@link CallRepository} abstraction, never on Spring
 * Data or the {@link CallEntity} mapping.
 */
public interface CallJpaRepository extends JpaRepository<CallEntity, String> {
}
