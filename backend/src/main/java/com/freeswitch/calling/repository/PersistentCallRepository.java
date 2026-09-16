package com.freeswitch.calling.repository;

import com.freeswitch.calling.config.FreeSwitchProperties;
import com.freeswitch.calling.dto.CallResponse;
import com.freeswitch.calling.entity.CallEntity;
import com.freeswitch.calling.model.Call;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Archives a finished call into the pre-existing {@code cdr} table, exactly
 * once, when it reaches a terminal state - see the {@code CHANNEL_HANGUP_COMPLETE}
 * handling in {@link com.freeswitch.calling.freeswitch.FreeSwitchEventListener}.
 * Also serves call history back out ({@link #findCallHistory()}), reading
 * from that same table.
 *
 * <p>Deliberately <em>not</em> a {@link CallRepository}: live, in-progress
 * call status (what {@code GET /api/v1/voice/calls/{callId}} reads) is
 * served entirely from memory by {@link InMemoryCallRepository}. Writes here
 * happen only once per call, matching how a call-detail record is
 * conventionally used - a historical record of a finished call, not a row
 * this API updates afterward.
 */
@Component
public class PersistentCallRepository {

    private final CallJpaRepository jpaRepository;
    private final FreeSwitchProperties freeSwitchProperties;

    public PersistentCallRepository(CallJpaRepository jpaRepository, FreeSwitchProperties freeSwitchProperties) {
        this.jpaRepository = jpaRepository;
        this.freeSwitchProperties = freeSwitchProperties;
    }

    /**
     * Writes {@code call} to the {@code cdr} table. Must only be called once
     * the call has reached a terminal state ({@link Call#isTerminal()}) -
     * a CDR is a one-time historical record, never updated afterward.
     */
    public void record(Call call) {
        Instant start = call.getCreatedAt();
        Instant answered = call.getAnsweredAt();
        Instant end = call.getCompletedAt();

        Integer duration = (start != null && end != null)
                ? (int) Duration.between(start, end).getSeconds()
                : null;
        // billsec (billable seconds) is conventionally the answer-to-hangup
        // span, 0 for a call that was never answered - not null, so an
        // unanswered call still reports zero billable time rather than
        // "unknown".
        int billsec = (answered != null && end != null)
                ? (int) Duration.between(answered, end).getSeconds()
                : 0;

        CallEntity entity = new CallEntity(
                call.getCallId(),
                freeSwitchProperties.getOriginate().getCallerIdName(),
                call.getFrom(),
                call.getTo(),
                // context isn't tracked by this application (it only ever
                // provisions/dials within FreeSWITCH's "default" directory
                // context - see FreeSwitchProperties.Directory), so it's
                // hard-coded here rather than left null.
                "default",
                start,
                answered,
                end,
                duration,
                billsec,
                call.getHangupCause(),
                call.getBlegUuid());

        jpaRepository.save(entity);
    }

    /**
     * All archived calls from the {@code cdr} table, most recently started
     * first. Only calls that have already reached a terminal state appear
     * here - a call still in progress hasn't been written yet (see
     * {@link #record}), so it isn't visible via this method until it hangs
     * up; use the live, in-memory {@link CallRepository} for that.
     */
    public List<CallResponse> findCallHistory() {
        return jpaRepository.findAll(Sort.by(Sort.Direction.DESC, "startStamp")).stream()
                .map(CallResponse::from)
                .toList();
    }

    /**
     * A single archived call by its {@code uuid}. Used as the fallback for
     * {@code GET /api/v1/voice/calls/{callId}} once a call has fallen out of
     * the in-memory {@link CallRepository} - e.g. after an application
     * restart, since {@link InMemoryCallRepository} doesn't survive one but
     * the {@code cdr} row does.
     */
    public Optional<CallResponse> findByCallId(String callId) {
        return jpaRepository.findById(callId).map(CallResponse::from);
    }
}
