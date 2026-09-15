package com.freeswitch.calling.service;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.dto.CreateVoiceUserRequest;
import com.freeswitch.calling.dto.DeleteVoiceUserResponse;
import com.freeswitch.calling.dto.VoiceUserResponse;
import com.freeswitch.calling.exception.ProtectedVoiceUserException;
import com.freeswitch.calling.exception.VoiceUserAlreadyExistsException;
import com.freeswitch.calling.exception.VoiceUserNotFoundException;
import com.freeswitch.calling.model.VoiceUser;
import com.freeswitch.calling.model.VoiceUserSource;
import com.freeswitch.calling.model.VoiceUserStatus;
import com.freeswitch.calling.repository.VoiceUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceUserServiceTest {

    private VoiceUserRepository voiceUserRepository;
    private FreeSwitchProperties properties;
    private VoiceUserService voiceUserService;

    @BeforeEach
    void setUp() {
        voiceUserRepository = mock(VoiceUserRepository.class);
        properties = new FreeSwitchProperties();
        properties.getDirectory().setProtectedExtensions(List.of("1001", "1002"));
        voiceUserService = new VoiceUserService(voiceUserRepository, properties);
    }

    @Test
    void shouldCreateNewVoiceUser() {
        when(voiceUserRepository.existsByExtension("1003")).thenReturn(false);
        when(voiceUserRepository.save(eq("1003"), anyString(), eq("Test User")))
                .thenReturn(new VoiceUser("1003", "Test User", VoiceUserStatus.ACTIVE, VoiceUserSource.PROVISIONED_BY_API));

        VoiceUserResponse response = voiceUserService.createUser(new CreateVoiceUserRequest("1003", "1234", "Test User"));

        assertThat(response.extension()).isEqualTo("1003");
        assertThat(response.name()).isEqualTo("Test User");
        assertThat(response.status()).isEqualTo(VoiceUserStatus.ACTIVE);
        assertThat(response.source()).isEqualTo(VoiceUserSource.PROVISIONED_BY_API);
    }

    @Test
    void shouldRejectDuplicateExtension() {
        when(voiceUserRepository.existsByExtension("1001")).thenReturn(true);

        assertThatThrownBy(() -> voiceUserService.createUser(new CreateVoiceUserRequest("1001", "newpassword", "Another User")))
                .isInstanceOf(VoiceUserAlreadyExistsException.class);

        verify(voiceUserRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotExposePasswordAnywhereInTheResponse() throws Exception {
        when(voiceUserRepository.existsByExtension("1003")).thenReturn(false);
        when(voiceUserRepository.save(eq("1003"), anyString(), anyString()))
                .thenReturn(new VoiceUser("1003", "Test User", VoiceUserStatus.ACTIVE, VoiceUserSource.PROVISIONED_BY_API));

        VoiceUserResponse response = voiceUserService.createUser(new CreateVoiceUserRequest("1003", "super-secret", "Test User"));

        // Structural guarantee: VoiceUserResponse has no component that could carry a
        // password, so there is nothing for this assertion to ever find - this test
        // exists as an explicit regression guard should a field ever be added.
        for (RecordComponent component : VoiceUserResponse.class.getRecordComponents()) {
            assertThat(component.getName()).doesNotContainIgnoringCase("password");
        }
        assertThat(response.toString()).doesNotContain("super-secret");
    }

    @Test
    void listUsersMapsRepositoryResults() {
        when(voiceUserRepository.findAll()).thenReturn(List.of(
                new VoiceUser("1001", "Extension 1001", VoiceUserStatus.ACTIVE, VoiceUserSource.EXISTING_EXTERNAL_USER),
                new VoiceUser("1003", "Test User", VoiceUserStatus.ACTIVE, VoiceUserSource.PROVISIONED_BY_API)));

        List<VoiceUserResponse> responses = voiceUserService.listUsers();

        assertThat(responses).extracting(VoiceUserResponse::extension).containsExactly("1001", "1003");
    }

    @Test
    void getUserThrowsNotFoundWhenMissing() {
        when(voiceUserRepository.findByExtension("1099")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceUserService.getUser("1099"))
                .isInstanceOf(VoiceUserNotFoundException.class);
    }

    @Test
    void shouldDeleteVoiceUser() {
        when(voiceUserRepository.existsByExtension("1003")).thenReturn(true);

        DeleteVoiceUserResponse response = voiceUserService.deleteUser("1003");

        assertThat(response.extension()).isEqualTo("1003");
        assertThat(response.status()).isEqualTo("DELETED");
        verify(voiceUserRepository).deleteByExtension("1003");
    }

    @Test
    void shouldRejectDeleteOfProtectedUser() {
        assertThatThrownBy(() -> voiceUserService.deleteUser("1001"))
                .isInstanceOf(ProtectedVoiceUserException.class);

        // The protection check must happen before touching the repository at all.
        verify(voiceUserRepository, never()).existsByExtension(anyString());
        verify(voiceUserRepository, never()).deleteByExtension(anyString());
    }

    @Test
    void deleteOfMissingExtensionThrowsNotFound() {
        when(voiceUserRepository.existsByExtension("1099")).thenReturn(false);

        assertThatThrownBy(() -> voiceUserService.deleteUser("1099"))
                .isInstanceOf(VoiceUserNotFoundException.class);
    }
}
