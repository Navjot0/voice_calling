package com.freeswitch.calling.freeswitch;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.exception.FreeSwitchOperationException;
import com.freeswitch.calling.exception.InvalidVoiceUserExtensionException;
import com.freeswitch.calling.exception.VoiceUserAlreadyExistsException;
import com.freeswitch.calling.exception.VoiceUserNotFoundException;
import com.freeswitch.calling.model.VoiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link FreeSwitchDirectoryService} against a real temp directory
 * on disk (FreeSWITCH's own ESL connection is the only thing mocked), so the
 * XML generator and file-safety guarantees are verified end to end: valid,
 * escaped XML on disk, atomic writes, and no path traversal.
 */
class FreeSwitchDirectoryServiceTest {

    @TempDir
    Path directory;

    private FreeSwitchClient freeSwitchClient;
    private FreeSwitchDirectoryService directoryService;

    @BeforeEach
    void setUp() {
        FreeSwitchProperties properties = new FreeSwitchProperties();
        properties.getDirectory().setPath(directory.toString());

        freeSwitchClient = mock(FreeSwitchClient.class);
        when(freeSwitchClient.executeSyncApi(anyString(), anyString())).thenReturn("+OK");

        directoryService = new FreeSwitchDirectoryService(properties, freeSwitchClient);
    }

    @Test
    void shouldCreateNewVoiceUser() throws Exception {
        directoryService.createUser("1003", "1234", "Test User");

        Path file = directory.resolve("1003.xml");
        assertThat(file).exists();

        Document doc = parse(file);
        assertThat(doc.getElementsByTagName("user").item(0).getAttributes().getNamedItem("id").getNodeValue())
                .isEqualTo("1003");
        assertThat(findParam(doc, "password")).isEqualTo("1234");
        assertThat(findVariable(doc, "effective_caller_id_name")).isEqualTo("Test User");
        assertThat(findVariable(doc, "effective_caller_id_number")).isEqualTo("1003");

        verify(freeSwitchClient, times(1)).executeSyncApi("reloadxml", "");
    }

    @Test
    void shouldRejectDuplicateExtension() {
        directoryService.createUser("1003", "1234", "Test User");

        assertThatThrownBy(() -> directoryService.createUser("1003", "other", "Someone Else"))
                .isInstanceOf(VoiceUserAlreadyExistsException.class);

        // The original file must be untouched.
        assertThat(directory.resolve("1003.xml")).exists();
    }

    @Test
    void shouldRejectInvalidExtension() {
        assertThatThrownBy(() -> directoryService.createUser("abc", "1234", "Test User"))
                .isInstanceOf(InvalidVoiceUserExtensionException.class);
        assertThatThrownBy(() -> directoryService.createUser("", "1234", "Test User"))
                .isInstanceOf(InvalidVoiceUserExtensionException.class);
    }

    @Test
    void shouldPreventPathTraversal() throws IOException {
        assertThatThrownBy(() -> directoryService.createUser("../../etc/passwd", "1234", "Test User"))
                .isInstanceOf(InvalidVoiceUserExtensionException.class);
        assertThatThrownBy(() -> directoryService.createUser("1003/../../../etc/passwd", "1234", "Test User"))
                .isInstanceOf(InvalidVoiceUserExtensionException.class);

        // Nothing was written anywhere, including outside the configured directory.
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void shouldEscapeXmlValues() throws Exception {
        String maliciousName = "</variables></user></include><evil>injected</evil>";
        String maliciousPassword = "p\"&<>'ss";

        directoryService.createUser("1003", maliciousPassword, maliciousName);

        // If escaping had failed, this file would either fail to parse as XML,
        // or the injected <evil> element would appear as real markup.
        Document doc = parse(directory.resolve("1003.xml"));
        assertThat(doc.getElementsByTagName("evil").getLength()).isZero();
        assertThat(findVariable(doc, "effective_caller_id_name")).isEqualTo(maliciousName);
        assertThat(findParam(doc, "password")).isEqualTo(maliciousPassword);
    }

    @Test
    void shouldDeleteVoiceUser() {
        directoryService.createUser("1003", "1234", "Test User");

        directoryService.deleteUser("1003", Set.of("1001", "1002"));

        assertThat(directory.resolve("1003.xml")).doesNotExist();
        assertThat(directoryService.readUser("1003")).isEmpty();
        verify(freeSwitchClient, times(2)).executeSyncApi("reloadxml", ""); // create + delete
    }

    @Test
    void shouldRejectDeleteOfProtectedExtension() {
        directoryService.createUser("1001", "1234", "Existing User");

        assertThatThrownBy(() -> directoryService.deleteUser("1001", Set.of("1001", "1002")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(directory.resolve("1001.xml")).exists();
    }

    @Test
    void deleteOfMissingExtensionThrowsNotFound() {
        assertThatThrownBy(() -> directoryService.deleteUser("1099", Set.of("1001", "1002")))
                .isInstanceOf(VoiceUserNotFoundException.class);
    }

    @Test
    void shouldListExistingAndCreatedUsers() {
        writeRawFile("1001", vanillaUserXml("1001", "1001", "Extension 1001"));
        writeRawFile("1002", vanillaUserXml("1002", "1002", "Extension 1002"));
        directoryService.createUser("1003", "1234", "Test User");

        List<VoiceUser> users = directoryService.listUsers();

        assertThat(users).extracting(VoiceUser::extension).containsExactlyInAnyOrder("1001", "1002", "1003");
    }

    @Test
    void listIgnoresNonUserConfigFilesInTheSameDirectory() throws IOException {
        // A real FreeSWITCH directory context commonly holds these alongside
        // per-extension files - none of them are SIP users and must never be
        // listed as one, regardless of their internal structure.
        Files.writeString(directory.resolve("default.xml"), "<include/>");
        Files.writeString(directory.resolve("example.com.xml"), "<include/>");
        Files.writeString(directory.resolve("skinny-example.xml"), "<include><user id=\"not-numeric\"/></include>");
        directoryService.createUser("1003", "1234", "Test User");

        List<VoiceUser> users = directoryService.listUsers();

        assertThat(users).extracting(VoiceUser::extension).containsExactly("1003");
    }

    @Test
    void listSkipsFilesThatFailToParseInsteadOfFailingEntirely() throws IOException {
        Files.writeString(directory.resolve("broken.xml"), "not valid xml <<<");
        directoryService.createUser("1003", "1234", "Test User");

        List<VoiceUser> users = directoryService.listUsers();

        assertThat(users).extracting(VoiceUser::extension).containsExactly("1003");
    }

    @Test
    void surfacesReloadFailureAsOperationException() {
        when(freeSwitchClient.executeSyncApi(anyString(), anyString()))
                .thenThrow(new FreeSwitchOperationException("reload failed"));

        assertThatThrownBy(() -> directoryService.createUser("1003", "1234", "Test User"))
                .isInstanceOf(FreeSwitchOperationException.class);

        // The file itself was still written before the reload step failed.
        assertThat(directory.resolve("1003.xml")).exists();
    }

    private void writeRawFile(String extension, String xml) {
        try {
            Files.writeString(directory.resolve(extension + ".xml"), xml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String vanillaUserXml(String extension, String password, String name) {
        return """
                <include>
                  <user id="%s">
                    <params>
                      <param name="password" value="%s"/>
                      <param name="vm-password" value="%s"/>
                    </params>
                    <variables>
                      <variable name="effective_caller_id_name" value="%s"/>
                      <variable name="effective_caller_id_number" value="%s"/>
                    </variables>
                  </user>
                </include>
                """.formatted(extension, password, extension, name, extension);
    }

    private Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private String findParam(Document doc, String name) {
        var params = doc.getElementsByTagName("param");
        for (int i = 0; i < params.getLength(); i++) {
            var el = params.item(i);
            if (name.equals(el.getAttributes().getNamedItem("name").getNodeValue())) {
                return el.getAttributes().getNamedItem("value").getNodeValue();
            }
        }
        throw new AssertionError("param not found: " + name);
    }

    private String findVariable(Document doc, String name) {
        var vars = doc.getElementsByTagName("variable");
        for (int i = 0; i < vars.getLength(); i++) {
            var el = vars.item(i);
            if (name.equals(el.getAttributes().getNamedItem("name").getNodeValue())) {
                return el.getAttributes().getNamedItem("value").getNodeValue();
            }
        }
        throw new AssertionError("variable not found: " + name);
    }
}
