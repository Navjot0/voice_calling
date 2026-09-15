package com.freeswitch.calling.controller;

import com.freeswitch.calling.dto.CreateVoiceUserRequest;
import com.freeswitch.calling.dto.DeleteVoiceUserResponse;
import com.freeswitch.calling.dto.VoiceUserResponse;
import com.freeswitch.calling.service.VoiceUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST entry point for dynamic SIP user/extension provisioning. Contains no
 * FreeSWITCH configuration logic itself - it only translates HTTP to/from
 * {@link VoiceUserService} calls.
 */
@RestController
@RequestMapping("/api/v1/voice/users")
@Validated
public class VoiceUserController {

    private static final String EXTENSION_PATTERN = "^[0-9]{2,15}$";

    private final VoiceUserService voiceUserService;

    public VoiceUserController(VoiceUserService voiceUserService) {
        this.voiceUserService = voiceUserService;
    }

    @PostMapping
    public ResponseEntity<VoiceUserResponse> createUser(@Valid @RequestBody CreateVoiceUserRequest request) {
        VoiceUserResponse response = voiceUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<VoiceUserResponse>> listUsers() {
        return ResponseEntity.ok(voiceUserService.listUsers());
    }

    @GetMapping("/{extension}")
    public ResponseEntity<VoiceUserResponse> getUser(
            @PathVariable @Pattern(regexp = EXTENSION_PATTERN, message = "Extension must be numeric (2-15 digits)") String extension) {
        return ResponseEntity.ok(voiceUserService.getUser(extension));
    }

    @DeleteMapping("/{extension}")
    public ResponseEntity<DeleteVoiceUserResponse> deleteUser(
            @PathVariable @Pattern(regexp = EXTENSION_PATTERN, message = "Extension must be numeric (2-15 digits)") String extension) {
        return ResponseEntity.ok(voiceUserService.deleteUser(extension));
    }
}
