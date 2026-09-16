package com.freeswitch.calling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping for a finished call, written to the pre-existing {@code cdr}
 * table - the standard FreeSWITCH call-detail-record table. This application
 * writes into that existing table rather than creating one of its own, so
 * the mapping only covers the columns it actually populates; every other
 * column ({@code accountcode}, {@code read_codec}, {@code write_codec},
 * {@code local_ip_v4}) is left {@code null} on insert since this application
 * has no source for those values.
 *
 * <p>There is no {@code direction}/{@code status} column on {@code cdr} (nor
 * in this mapping) - this API only ever originates OUTBOUND calls, and a
 * finished call's outcome is already fully captured by its timestamps and
 * {@code hangup_cause}, matching how a call-detail record is conventionally
 * read. See {@link com.freeswitch.calling.repository.PersistentCallRepository}
 * for how a {@link com.freeswitch.calling.model.Call} - the *live*,
 * in-progress domain object - is converted into this one-time archival row.
 */
@Entity
@Table(name = "cdr")
public class CallEntity {

    @Id
    @Column(name = "uuid", nullable = false, updatable = false, length = 100)
    private String uuid;

    @Column(name = "caller_id_name", length = 255)
    private String callerIdName;

    @Column(name = "caller_id_number", length = 100)
    private String callerIdNumber;

    @Column(name = "destination_number", length = 100)
    private String destinationNumber;

    @Column(name = "context", length = 100)
    private String context;

    @Column(name = "start_stamp")
    private Instant startStamp;

    @Column(name = "answer_stamp")
    private Instant answerStamp;

    @Column(name = "end_stamp")
    private Instant endStamp;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "billsec")
    private Integer billsec;

    @Column(name = "hangup_cause", length = 100)
    private String hangupCause;

    @Column(name = "bleg_uuid", length = 100)
    private String blegUuid;

    /** Required by JPA; not for application use. */
    protected CallEntity() {
    }

    public CallEntity(String uuid, String callerIdName, String callerIdNumber, String destinationNumber,
                       String context, Instant startStamp, Instant answerStamp, Instant endStamp,
                       Integer duration, Integer billsec, String hangupCause, String blegUuid) {
        this.uuid = uuid;
        this.callerIdName = callerIdName;
        this.callerIdNumber = callerIdNumber;
        this.destinationNumber = destinationNumber;
        this.context = context;
        this.startStamp = startStamp;
        this.answerStamp = answerStamp;
        this.endStamp = endStamp;
        this.duration = duration;
        this.billsec = billsec;
        this.hangupCause = hangupCause;
        this.blegUuid = blegUuid;
    }

    public String getUuid() {
        return uuid;
    }

    public String getCallerIdName() {
        return callerIdName;
    }

    public String getCallerIdNumber() {
        return callerIdNumber;
    }

    public String getDestinationNumber() {
        return destinationNumber;
    }

    public String getContext() {
        return context;
    }

    public Instant getStartStamp() {
        return startStamp;
    }

    public Instant getAnswerStamp() {
        return answerStamp;
    }

    public Instant getEndStamp() {
        return endStamp;
    }

    public Integer getDuration() {
        return duration;
    }

    public Integer getBillsec() {
        return billsec;
    }

    public String getHangupCause() {
        return hangupCause;
    }

    public String getBlegUuid() {
        return blegUuid;
    }
}
