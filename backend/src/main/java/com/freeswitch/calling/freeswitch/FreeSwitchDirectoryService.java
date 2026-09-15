package com.freeswitch.calling.freeswitch;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.exception.FreeSwitchOperationException;
import com.freeswitch.calling.exception.InvalidVoiceUserExtensionException;
import com.freeswitch.calling.exception.VoiceUserAlreadyExistsException;
import com.freeswitch.calling.exception.VoiceUserNotFoundException;
import com.freeswitch.calling.model.VoiceUser;
import com.freeswitch.calling.model.VoiceUserSource;
import com.freeswitch.calling.model.VoiceUserStatus;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Owns all filesystem interaction with FreeSWITCH's on-disk directory
 * (default context) - generating, reading, listing and deleting per-user XML
 * files - plus triggering FreeSWITCH's {@code reloadxml} so a change becomes
 * live. This is the isolation boundary the rest of the provisioning feature
 * depends on: nothing above this class knows the directory is a folder of XML
 * files, and nothing below it is reachable except through the methods here.
 *
 * <p>This assumes the application runs on the same host as FreeSWITCH, since
 * {@code freeswitch.directory.path} is a local filesystem path FreeSWITCH
 * itself reads from - see the note on that property.
 *
 * <p>Generated files follow the structure of this server's actual existing
 * {@code 1001.xml}/{@code 1002.xml} (inspected directly, not assumed):
 * {@code password}/{@code vm-password} params, and
 * {@code toll_allow}/{@code accountcode}/{@code user_context}/
 * {@code effective_caller_id_name}/{@code effective_caller_id_number}/
 * {@code outbound_caller_id_name}/{@code outbound_caller_id_number}/
 * {@code callgroup} variables.
 */
@Component
public class FreeSwitchDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchDirectoryService.class);

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[0-9]{2,15}$");
    private static final String XML_SUFFIX = ".xml";

    private final FreeSwitchProperties properties;
    private final FreeSwitchClient freeSwitchClient;
    private final Path directoryPath;

    public FreeSwitchDirectoryService(FreeSwitchProperties properties, FreeSwitchClient freeSwitchClient) {
        this.properties = properties;
        this.freeSwitchClient = freeSwitchClient;
        this.directoryPath = Paths.get(properties.getDirectory().getPath()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void logConfiguredPath() {
        log.info("FreeSWITCH directory provisioning path: {}", directoryPath);
    }

    /**
     * Creates {@code <extension>.xml} in the directory and reloads FreeSWITCH.
     * Never overwrites an existing file - the atomic move at the end fails
     * outright if the target already exists, which is what protects 1001/1002
     * (and any other existing user) even under concurrent requests.
     */
    public void createUser(String extension, String password, String name) {
        validateExtension(extension);
        Path target = resolveUserFilePath(extension);
        if (Files.exists(target)) {
            throw new VoiceUserAlreadyExistsException(extension);
        }

        String xml = buildUserXml(extension, password, name);
        writeAtomically(extension, target, xml);
        log.info("Generated directory configuration for extension {}", extension);

        reloadDirectory();
        log.info("Extension {} created successfully", extension);
    }

    public Optional<VoiceUser> readUser(String extension) {
        validateExtension(extension);
        Path target = resolveUserFilePath(extension);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(parseUserFile(target, extension));
    }

    /**
     * Lists every per-extension user directory XML file found, skipping any
     * that fail to parse.
     *
     * <p>Only files whose name is itself a valid numeric extension (e.g.
     * {@code 1001.xml}) are considered - a real FreeSWITCH directory context
     * also commonly holds non-user config files alongside per-extension ones
     * (e.g. {@code default.xml}, a gateway/domain file such as
     * {@code example.com.xml}, a custom file like {@code skinny-example.xml}
     * or {@code brian.xml}), which have a different structure entirely and
     * must never be listed, read, or touched as if they were SIP users.
     */
    public List<VoiceUser> listUsers() {
        try (Stream<Path> files = Files.list(directoryPath)) {
            return files
                    .filter(p -> isUserFileName(p.getFileName().toString()))
                    .sorted()
                    .map(this::tryParseUserFile)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new FreeSwitchOperationException("Failed to list FreeSWITCH directory users", e);
        }
    }

    private boolean isUserFileName(String fileName) {
        if (!fileName.endsWith(XML_SUFFIX)) {
            return false;
        }
        return EXTENSION_PATTERN.matcher(stripXmlSuffix(fileName)).matches();
    }

    /**
     * Deletes {@code <extension>.xml} and reloads FreeSWITCH.
     *
     * @param protectedExtensions extensions that must never be deleted here,
     *                            regardless of caller - a hard safety net in
     *                            addition to the business-rule check in
     *                            {@code VoiceUserService}, since this is the
     *                            one place that actually removes the file.
     */
    public void deleteUser(String extension, Set<String> protectedExtensions) {
        validateExtension(extension);
        if (protectedExtensions.contains(extension)) {
            // Should be unreachable - VoiceUserService checks this first and
            // returns a proper 403 without ever calling down here.
            throw new IllegalStateException("Refusing to delete protected extension " + extension);
        }

        Path target = resolveUserFilePath(extension);
        if (!Files.exists(target)) {
            throw new VoiceUserNotFoundException(extension);
        }

        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new FreeSwitchOperationException("Failed to delete directory file for extension " + extension, e);
        }

        reloadDirectory();
    }

    private void reloadDirectory() {
        freeSwitchClient.executeSyncApi("reloadxml", "");
        log.info("FreeSWITCH directory reload requested");
    }

    // ---- filename / path safety -------------------------------------------------

    private void validateExtension(String extension) {
        if (extension == null || !EXTENSION_PATTERN.matcher(extension).matches()) {
            throw new InvalidVoiceUserExtensionException(String.valueOf(extension));
        }
    }

    private Path resolveUserFilePath(String extension) {
        Path target = directoryPath.resolve(extension + XML_SUFFIX).normalize();
        // Defense in depth: the regex above already makes traversal characters
        // ('/', '.', etc.) impossible in `extension`, so this should never trip -
        // but a resolved path outside the configured directory is refused outright
        // rather than trusted.
        if (!target.getParent().equals(directoryPath)) {
            throw new InvalidVoiceUserExtensionException(extension);
        }
        return target;
    }

    // ---- atomic, safe file writing -----------------------------------------------

    private void writeAtomically(String extension, Path target, String content) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(directoryPath, target.getFileName().toString(), ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            // No REPLACE_EXISTING: if the target now exists (e.g. a concurrent
            // request won the race), this throws instead of silently overwriting it.
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            cleanup(tmp);
            throw new VoiceUserAlreadyExistsException(extension);
        } catch (IOException e) {
            cleanup(tmp);
            throw new FreeSwitchOperationException(
                    "Failed to write directory configuration file for extension " + extension, e);
        }
    }

    private void cleanup(Path tmp) {
        if (tmp == null) {
            return;
        }
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // Best-effort cleanup of a temp file; leaving a stray .tmp file behind
            // is harmless and not worth failing the original operation over.
        }
    }

    // ---- XML generation (DOM-based: values are escaped automatically) ------------

    /**
     * Builds the directory XML for a new user via the DOM API rather than
     * string concatenation, so every value ({@code extension}, {@code password},
     * {@code name}) is escaped automatically by the serializer - arbitrary XML
     * injection through the API is structurally not possible here.
     */
    private String buildUserXml(String extension, String password, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element include = doc.createElement("include");
            doc.appendChild(include);

            Element user = doc.createElement("user");
            user.setAttribute("id", extension);
            include.appendChild(user);

            Element params = doc.createElement("params");
            user.appendChild(params);
            params.appendChild(paramElement(doc, "password", password));
            params.appendChild(paramElement(doc, "vm-password", extension));

            Element variables = doc.createElement("variables");
            user.appendChild(variables);
            variables.appendChild(variableElement(doc, "toll_allow", "domestic,international,local"));
            variables.appendChild(variableElement(doc, "accountcode", extension));
            variables.appendChild(variableElement(doc, "user_context", "default"));
            variables.appendChild(variableElement(doc, "effective_caller_id_name", name));
            variables.appendChild(variableElement(doc, "effective_caller_id_number", extension));
            variables.appendChild(variableElement(doc, "outbound_caller_id_name", "$${outbound_caller_name}"));
            variables.appendChild(variableElement(doc, "outbound_caller_id_number", "$${outbound_caller_id}"));
            variables.appendChild(variableElement(doc, "callgroup", "techsupport"));

            return serialize(doc);
        } catch (ParserConfigurationException | TransformerException e) {
            throw new FreeSwitchOperationException("Failed to generate directory XML for extension " + extension, e);
        }
    }

    private Element paramElement(Document doc, String name, String value) {
        Element param = doc.createElement("param");
        param.setAttribute("name", name);
        param.setAttribute("value", value);
        return param;
    }

    private Element variableElement(Document doc, String name, String value) {
        Element variable = doc.createElement("variable");
        variable.setAttribute("name", name);
        variable.setAttribute("value", value);
        return variable;
    }

    private String serialize(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    // ---- reading / parsing existing files -----------------------------------------

    private VoiceUser tryParseUserFile(Path path) {
        String extension = stripXmlSuffix(path.getFileName().toString());
        try {
            return parseUserFile(path, extension);
        } catch (Exception e) {
            log.warn("Skipping unparseable directory file {}: {}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    /**
     * Parses an existing directory XML file into a {@link VoiceUser}. Hardened
     * against XXE the same way generation is hardened against injection -
     * these files are normally ones this service wrote, but a directory folder
     * can contain entries this application did not create (e.g. 1001/1002).
     */
    private VoiceUser parseUserFile(Path path, String extensionFromFilename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(path.toFile());

            NodeList userNodes = doc.getElementsByTagName("user");
            String id = userNodes.getLength() > 0
                    ? ((Element) userNodes.item(0)).getAttribute("id")
                    : extensionFromFilename;
            String extension = id.isBlank() ? extensionFromFilename : id;

            String name = findVariable(doc, "effective_caller_id_name").orElse(extension);

            // Source is unknown at this layer (this service has no notion of "the
            // API created this"); the repository re-tags it based on its own
            // in-memory provenance tracking.
            return new VoiceUser(extension, name, VoiceUserStatus.ACTIVE, VoiceUserSource.EXISTING_EXTERNAL_USER);
        } catch (Exception e) {
            throw new FreeSwitchOperationException("Failed to parse directory file " + path.getFileName(), e);
        }
    }

    private Optional<String> findVariable(Document doc, String variableName) {
        NodeList variables = doc.getElementsByTagName("variable");
        for (int i = 0; i < variables.getLength(); i++) {
            Element variable = (Element) variables.item(i);
            if (variableName.equals(variable.getAttribute("name"))) {
                return Optional.of(variable.getAttribute("value"));
            }
        }
        return Optional.empty();
    }

    private String stripXmlSuffix(String fileName) {
        return fileName.endsWith(XML_SUFFIX) ? fileName.substring(0, fileName.length() - XML_SUFFIX.length()) : fileName;
    }
}
