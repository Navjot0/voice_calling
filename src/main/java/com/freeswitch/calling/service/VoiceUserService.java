package com.freeswitch.calling.service;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.dto.CreateVoiceUserRequest;
import com.freeswitch.calling.dto.DeleteVoiceUserResponse;
import com.freeswitch.calling.dto.VoiceUserResponse;
import com.freeswitch.calling.exception.ProtectedVoiceUserException;
import com.freeswitch.calling.exception.VoiceUserAlreadyExistsException;
import com.freeswitch.calling.exception.VoiceUserNotFoundException;
import com.freeswitch.calling.model.VoiceUser;
import com.freeswitch.calling.repository.VoiceUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for SIP user/extension provisioning: existence and
 * protected-extension rules live here, above the storage abstraction. Never
 * touches FreeSWITCH or the filesystem directly - see
 * {@code VoiceUserRepository} / {@code FreeSwitchDirectoryService} for that.
 */
@Service
public class VoiceUserService {

    private static final Logger log = LoggerFactory.getLogger(VoiceUserService.class);

    private final VoiceUserRepository voiceUserRepository;
    private final FreeSwitchProperties properties;

    public VoiceUserService(VoiceUserRepository voiceUserRepository, FreeSwitchProperties properties) {
        this.voiceUserRepository = voiceUserRepository;
        this.properties = properties;
    }

    public VoiceUserResponse createUser(CreateVoiceUserRequest request) {
        String extension = request.extension().trim();
        String name = request.name().trim();

        log.info("Creating FreeSWITCH extension {}", extension);

        if (voiceUserRepository.existsByExtension(extension)) {
            throw new VoiceUserAlreadyExistsException(extension);
        }

        // request.password() is intentionally never logged - see
        // CreateVoiceUserRequest's overridden toString() for the same reason.
        VoiceUser user = voiceUserRepository.save(extension, request.password(), name);

        return VoiceUserResponse.from(user);
    }

    public List<VoiceUserResponse> listUsers() {
        return voiceUserRepository.findAll().stream()
                .map(VoiceUserResponse::from)
                .toList();
    }

    public VoiceUserResponse getUser(String extension) {
        VoiceUser user = voiceUserRepository.findByExtension(extension)
                .orElseThrow(() -> new VoiceUserNotFoundException(extension));
        return VoiceUserResponse.from(user);
    }

    public DeleteVoiceUserResponse deleteUser(String extension) {
        if (properties.getDirectory().getProtectedExtensions().contains(extension)) {
            throw new ProtectedVoiceUserException(extension);
        }
        if (!voiceUserRepository.existsByExtension(extension)) {
            throw new VoiceUserNotFoundException(extension);
        }

        log.info("Deleting FreeSWITCH extension {}", extension);
        voiceUserRepository.deleteByExtension(extension);
        log.info("Extension {} deleted successfully", extension);

        return DeleteVoiceUserResponse.deleted(extension);
    }
}
