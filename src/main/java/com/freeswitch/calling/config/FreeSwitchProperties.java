package com.freeswitch.calling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code freeswitch.*} configuration block. Keeping connection
 * details here (rather than hard-coded in the ESL client) is what lets the
 * password be overridden via the {@code FREESWITCH_PASSWORD} environment
 * variable in production, as required for this step.
 */
@ConfigurationProperties(prefix = "freeswitch")
public class FreeSwitchProperties {

    /** FreeSWITCH ESL host. */
    private String host = "localhost";

    /** FreeSWITCH ESL port. */
    private int port = 8021;

    /** FreeSWITCH ESL auth password. Never logged. */
    private String password = "ClueCon";

    /** Milliseconds allowed to establish the ESL socket connection. */
    private int connectionTimeout = 5000;

    /** Milliseconds allowed for a single FreeSWITCH command to complete. */
    private int commandTimeout = 10000;

    @NestedConfigurationProperty
    private Originate originate = new Originate();

    @NestedConfigurationProperty
    private Directory directory = new Directory();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(int commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    public Originate getOriginate() {
        return originate;
    }

    public void setOriginate(Originate originate) {
        this.originate = originate;
    }

    public Directory getDirectory() {
        return directory;
    }

    public void setDirectory(Directory directory) {
        this.directory = directory;
    }

    /** Settings that shape the dial string sent to FreeSWITCH's {@code originate} API. */
    public static class Originate {

        /** Prefix applied to an extension to build a FreeSWITCH dial string, e.g. "user/" -&gt; "user/1001". */
        private String dialPrefix = "user/";

        /** Caller ID name set on the originating leg. */
        private String callerIdName = "CPaaS";

        public String getDialPrefix() {
            return dialPrefix;
        }

        public void setDialPrefix(String dialPrefix) {
            this.dialPrefix = dialPrefix;
        }

        public String getCallerIdName() {
            return callerIdName;
        }

        public void setCallerIdName(String callerIdName) {
            this.callerIdName = callerIdName;
        }
    }

    /**
     * Settings for dynamic SIP user/extension provisioning against FreeSWITCH's
     * on-disk XML directory. Assumes this application runs on the same host as
     * FreeSWITCH (so {@code path} is a local filesystem path FreeSWITCH itself
     * reads from) - see {@link com.freeswitch.calling.freeswitch.FreeSwitchDirectoryService}.
     */
    public static class Directory {

        /** Local filesystem path to FreeSWITCH's default directory context. */
        private String path = "/usr/local/freeswitch/conf/directory/default";

        /**
         * Extensions that must never be deleted through the API, regardless of
         * who provisioned them. Defaults to the pre-existing 1001/1002 extensions
         * this platform did not create.
         */
        private List<String> protectedExtensions = new ArrayList<>(List.of("1001", "1002"));

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getProtectedExtensions() {
            return protectedExtensions;
        }

        public void setProtectedExtensions(List<String> protectedExtensions) {
            this.protectedExtensions = protectedExtensions;
        }
    }
}
