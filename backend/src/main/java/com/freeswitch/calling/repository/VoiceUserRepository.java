package com.freeswitch.calling.repository;

import com.freeswitch.calling.model.VoiceUser;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for SIP directory users, mirroring {@link CallRepository}'s
 * role for calls. The only implementation for now reads/writes FreeSWITCH's
 * on-disk directory XML through {@code FreeSwitchDirectoryService}; a future
 * PostgreSQL-backed implementation (e.g. once provisioning needs its own
 * metadata store) can replace it without the service or controller changing.
 */
public interface VoiceUserRepository {

    /** Creates a new directory user and makes it live. Throws if it already exists. */
    VoiceUser save(String extension, String password, String name);

    Optional<VoiceUser> findByExtension(String extension);

    List<VoiceUser> findAll();

    boolean existsByExtension(String extension);

    /** Removes a directory user and makes the removal live. Throws if it does not exist. */
    void deleteByExtension(String extension);
}
