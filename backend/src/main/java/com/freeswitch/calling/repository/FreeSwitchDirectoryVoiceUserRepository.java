package com.freeswitch.calling.repository;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.freeswitch.FreeSwitchDirectoryService;
import com.freeswitch.calling.model.VoiceUser;
import com.freeswitch.calling.model.VoiceUserSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link VoiceUserRepository} backed by FreeSWITCH's on-disk directory XML
 * (via {@link FreeSwitchDirectoryService}), which is this application's
 * source of truth for which users exist and what their data is.
 *
 * <p>The one thing the directory itself cannot tell us is provenance - it has
 * no notion of "created by this API" versus "a pre-existing extension like
 * 1001/1002". This class tracks that separately in a process-local set,
 * populated on {@link #save} and consulted on every read. It is
 * intentionally NOT persisted: it is metadata about API usage, not about
 * FreeSWITCH state, and per {@link VoiceUserRepository}'s docs a persistent
 * store (e.g. Postgres) can be introduced later purely for this bookkeeping
 * without changing this interface. Until then, this resets on restart, so an
 * extension created via the API before a restart shows as
 * {@link VoiceUserSource#EXISTING_EXTERNAL_USER} afterward - documented
 * behavior, not a bug.
 */
@Repository
public class FreeSwitchDirectoryVoiceUserRepository implements VoiceUserRepository {

    private final FreeSwitchDirectoryService directoryService;
    private final FreeSwitchProperties properties;
    private final Set<String> provisionedByApi = ConcurrentHashMap.newKeySet();

    public FreeSwitchDirectoryVoiceUserRepository(FreeSwitchDirectoryService directoryService,
                                                   FreeSwitchProperties properties) {
        this.directoryService = directoryService;
        this.properties = properties;
    }

    @Override
    public VoiceUser save(String extension, String password, String name) {
        directoryService.createUser(extension, password, name);
        provisionedByApi.add(extension);
        return directoryService.readUser(extension)
                .map(this::tagSource)
                // Should be unreachable: createUser() just wrote this file successfully.
                .orElseThrow(() -> new IllegalStateException(
                        "Extension " + extension + " was created but could not be read back"));
    }

    @Override
    public Optional<VoiceUser> findByExtension(String extension) {
        return directoryService.readUser(extension).map(this::tagSource);
    }

    @Override
    public List<VoiceUser> findAll() {
        return directoryService.listUsers().stream().map(this::tagSource).toList();
    }

    @Override
    public boolean existsByExtension(String extension) {
        return directoryService.readUser(extension).isPresent();
    }

    @Override
    public void deleteByExtension(String extension) {
        directoryService.deleteUser(extension, Set.copyOf(properties.getDirectory().getProtectedExtensions()));
        provisionedByApi.remove(extension);
    }

    private VoiceUser tagSource(VoiceUser raw) {
        VoiceUserSource source = provisionedByApi.contains(raw.extension())
                ? VoiceUserSource.PROVISIONED_BY_API
                : VoiceUserSource.EXISTING_EXTERNAL_USER;
        return new VoiceUser(raw.extension(), raw.name(), raw.status(), source);
    }
}
