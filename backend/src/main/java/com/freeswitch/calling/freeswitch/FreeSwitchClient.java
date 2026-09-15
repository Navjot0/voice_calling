package com.freeswitch.calling.freeswitch;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.exception.FreeSwitchConnectionException;
import com.freeswitch.calling.exception.FreeSwitchOperationException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reusable, persistent client for FreeSWITCH's Event Socket Library (ESL).
 *
 * <p>Owns the single inbound ESL connection for this application: it
 * connects and authenticates on startup, subscribes to the channel events
 * the platform tracks, reconnects on demand if the connection has dropped,
 * and exposes only high-level operations (e.g. {@link #originate}) to the
 * rest of the application. The raw {@link Client} is never exposed outside
 * this class.
 */
@Component
public class FreeSwitchClient {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchClient.class);

    private static final String TRACKED_EVENTS =
            "CHANNEL_CREATE CHANNEL_PROGRESS CHANNEL_ANSWER CHANNEL_HANGUP CHANNEL_HANGUP_COMPLETE BACKGROUND_JOB";

    private final FreeSwitchProperties properties;
    private final FreeSwitchEventListener eventListener;
    private final ExecutorService commandExecutor =
            Executors.newCachedThreadPool(runnable -> {
                Thread t = new Thread(runnable, "freeswitch-esl-command");
                t.setDaemon(true);
                return t;
            });

    private final Object connectionLock = new Object();
    private volatile Client eslClient;

    public FreeSwitchClient(FreeSwitchProperties properties, FreeSwitchEventListener eventListener) {
        this.properties = properties;
        this.eventListener = eventListener;
    }

    /**
     * Kicks off the initial connection attempt in the background so that a
     * FreeSWITCH outage never delays application startup; commands issued
     * before the connection is ready simply trigger a fresh attempt.
     */
    @PostConstruct
    public void init() {
        Thread t = new Thread(this::connect, "freeswitch-esl-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connect() {
        synchronized (connectionLock) {
            if (isConnected()) {
                return;
            }
            try {
                Client client = new Client();
                client.addEventListener(eventListener);

                int timeoutSeconds = Math.max(1, properties.getConnectionTimeout() / 1000);
                client.connect(properties.getHost(), properties.getPort(), properties.getPassword(), timeoutSeconds);

                CommandResponse subscription = client.setEventSubscriptions("plain", TRACKED_EVENTS);
                if (!subscription.isOk()) {
                    log.warn("FreeSWITCH event subscription was not acknowledged as OK: {}", subscription.getReplyText());
                }

                this.eslClient = client;
                log.info("FreeSWITCH ESL connection established host={} port={}", properties.getHost(), properties.getPort());
            } catch (InboundConnectionFailure e) {
                this.eslClient = null;
                log.error("Failed to connect to FreeSWITCH ESL at {}:{} - {}",
                        properties.getHost(), properties.getPort(), e.getMessage());
            } catch (Exception e) {
                this.eslClient = null;
                log.error("Unexpected error connecting to FreeSWITCH ESL at {}:{} - {}",
                        properties.getHost(), properties.getPort(), e.getMessage(), e);
            }
        }
    }

    public boolean isConnected() {
        Client client = this.eslClient;
        return client != null && client.canSend();
    }

    /**
     * Sends an ESL {@code originate} (via {@code bgapi}, non-blocking) that
     * dials {@code from} first and, on answer, bridges to {@code to}.
     *
     * <p>{@code originationUuid} is passed to FreeSWITCH as the
     * {@code origination_uuid} channel variable, so the channel FreeSWITCH
     * creates for the originating leg is guaranteed to carry this exact
     * UUID. This lets the caller know the definitive call ID immediately,
     * without waiting on or parsing an asynchronous job result, while the
     * ID itself remains the real FreeSWITCH channel UUID rather than an
     * unrelated one.
     *
     * @return the ESL background job UUID for the submitted command (useful
     *         for diagnostics only - call state is tracked via channel
     *         events keyed by {@code originationUuid}, not this job UUID).
     */
    public String originate(String originationUuid, String from, String to) {
        Client client = getConnectedClient();
        String dialString = buildDialString(originationUuid, from, to);

        log.info("Sending originate command callId={} from={} to={}", originationUuid, from, to);
        try {
            String jobUuid = CompletableFuture
                    .supplyAsync(() -> client.sendAsyncApiCommand("originate", dialString), commandExecutor)
                    .get(properties.getCommandTimeout(), TimeUnit.MILLISECONDS);
            log.info("Originate command accepted callId={} jobUuid={}", originationUuid, jobUuid);
            return jobUuid;
        } catch (TimeoutException e) {
            throw new FreeSwitchOperationException(
                    "Timed out sending originate command to FreeSWITCH for callId=" + originationUuid, e);
        } catch (Exception e) {
            throw new FreeSwitchOperationException(
                    "Failed to send originate command to FreeSWITCH for callId=" + originationUuid, e);
        }
    }

    /**
     * Executes a synchronous FreeSWITCH {@code api} command (as opposed to
     * {@code originate}'s fire-and-forget {@code bgapi}) and returns its raw
     * response body. Used for administrative operations - e.g. {@code reloadxml}
     * after provisioning a directory change - where the caller needs to know
     * the command actually completed before proceeding.
     */
    public String executeSyncApi(String command, String arg) {
        Client client = getConnectedClient();
        log.info("Sending FreeSWITCH api command={}", command);
        try {
            EslMessage message = CompletableFuture
                    .supplyAsync(() -> client.sendSyncApiCommand(command, arg), commandExecutor)
                    .get(properties.getCommandTimeout(), TimeUnit.MILLISECONDS);
            String body = String.join("\n", message.getBodyLines());
            log.info("FreeSWITCH api command={} completed", command);
            return body;
        } catch (TimeoutException e) {
            throw new FreeSwitchOperationException(
                    "Timed out executing FreeSWITCH command: " + command, e);
        } catch (Exception e) {
            throw new FreeSwitchOperationException(
                    "Failed to execute FreeSWITCH command: " + command, e);
        }
    }

    private Client getConnectedClient() {
        if (!isConnected()) {
            log.warn("FreeSWITCH ESL not connected - attempting reconnect before sending command");
            connect();
        }
        Client client = this.eslClient;
        if (client == null || !client.canSend()) {
            throw new FreeSwitchConnectionException(
                    "Unable to establish an ESL connection to FreeSWITCH at "
                            + properties.getHost() + ":" + properties.getPort());
        }
        return client;
    }

    private String buildDialString(String originationUuid, String from, String to) {
        FreeSwitchProperties.Originate cfg = properties.getOriginate();
        String originationLeg = cfg.getDialPrefix() + from;

        // The {..} block below only applies channel variables to the leg FreeSWITCH
        // creates for the "originate" command itself (the leg to `from`). The
        // &bridge() leg (to `to`) is a separate channel and does not inherit those
        // variables automatically here, so without its own [..] variable block it
        // falls back to FreeSWITCH's directory-lookup default caller ID for `to` -
        // i.e. the callee would see itself as the caller. Setting
        // origination_caller_id_number/_name explicitly on the bridge leg as well
        // ensures `to` sees `from` as the caller on both legs.
        String bridgeLegVars = "[origination_caller_id_number=" + from
                + ",origination_caller_id_name=" + cfg.getCallerIdName() + "]";
        String bridgeLeg = bridgeLegVars + cfg.getDialPrefix() + to;

        String channelVariables = "{origination_uuid=" + originationUuid
                + ",origination_caller_id_number=" + from
                + ",origination_caller_id_name=" + cfg.getCallerIdName()
                + ",ignore_early_media=true}";

        return channelVariables + originationLeg + " &bridge(" + bridgeLeg + ")";
    }

    @PreDestroy
    public void shutdown() {
        commandExecutor.shutdown();
        Client client = this.eslClient;
        if (client != null) {
            try {
                client.close();
                log.info("FreeSWITCH ESL connection closed");
            } catch (Exception e) {
                log.warn("Error while closing FreeSWITCH ESL connection: {}", e.getMessage());
            }
        }
    }
}
