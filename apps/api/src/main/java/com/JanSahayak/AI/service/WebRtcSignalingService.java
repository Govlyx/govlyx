package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.ChatMessage;
import com.JanSahayak.AI.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebRTC call state and delegates signaling relay to ChatMessagingService.
 *
 * ── How WebRTC calls work ─────────────────────────────────────────────────────
 *
 *  Caller                     Server (this service)               Callee
 *  ──────                     ─────────────────────               ──────
 *  initiateCall()  ─────────► relaySignal(CALL_OFFER)  ─────────► /queue/call
 *                                                                  ↓
 *  /queue/call ◄─────────────  relaySignal(CALL_ANSWER) ◄──────── acceptCall()
 *
 *  ICE candidates are exchanged the same way (CALL_ICE).
 *  Once both peers have negotiated SDP + ICE, the actual audio/video stream
 *  is peer-to-peer — this server is NOT in the media path at all.
 *
 * ── Storage policy ────────────────────────────────────────────────────────────
 * Call state (who is in a call, call type) is held in {@code activeCalls}
 * in memory only.  No SDP, ICE, or media ever touches the database.
 * {@code activeCalls} is cleaned up when the session ends or either user
 * sends CALL_ENDED.
 *
 * ── One call per session ──────────────────────────────────────────────────────
 * Enforces at most one concurrent call per chat session.  Attempting to
 * start a second call while one is active throws a RuntimeException, which
 * the controller maps to a 400 Bad Request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebRtcSignalingService {

    private final ChatSessionService   chatSessionService;
    private final ChatMessagingService chatMessagingService;

    public enum CallType { VOICE, VIDEO }

    /**
     * In-memory call registry — sessionId → CallRecord.
     * Bounded by the number of concurrent chat sessions (negligible heap).
     */
    private final Map<String, CallRecord> activeCalls = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start a call: validate state, record the call, relay CALL_OFFER to partner.
     *
     * @param sessionId active chat session
     * @param callerId  userId of the initiating user
     * @param callType  VOICE or VIDEO
     * @param sdpOffer  SDP offer JSON string from the browser RTCPeerConnection
     */
    public void initiateCall(String sessionId, Long callerId, CallType callType, String sdpOffer) {
        ChatSession session = chatSessionService.getSession(sessionId);
        validateSession(session, sessionId, callerId);

        if (activeCalls.containsKey(sessionId)) {
            throw new RuntimeException("A call is already active in session " + sessionId);
        }

        activeCalls.put(sessionId, new CallRecord(sessionId, callerId, callType));

        String callerAnonymousId = session.getUserAnonymousId(callerId);
        ChatMessage offer = ChatMessage.signalingMessage(
                sessionId, callerAnonymousId, ChatMessage.MessageType.CALL_OFFER, sdpOffer);

        chatMessagingService.relaySignal(sessionId, offer);
        log.info("Call initiated: session={} caller={} type={}", sessionId, callerId, callType);
    }

    /**
     * Accept a call: relay CALL_ANSWER back to the caller.
     *
     * @param sessionId active chat session
     * @param calleeId  userId of the answering user
     * @param sdpAnswer SDP answer JSON string from the browser RTCPeerConnection
     */
    public void acceptCall(String sessionId, Long calleeId, String sdpAnswer) {
        ChatSession session = chatSessionService.getSession(sessionId);
        validateSession(session, sessionId, calleeId);

        if (!activeCalls.containsKey(sessionId)) {
            throw new RuntimeException("No active call to answer in session " + sessionId);
        }

        String calleeAnonymousId = session.getUserAnonymousId(calleeId);
        ChatMessage answer = ChatMessage.signalingMessage(
                sessionId, calleeAnonymousId, ChatMessage.MessageType.CALL_ANSWER, sdpAnswer);

        chatMessagingService.relaySignal(sessionId, answer);
        log.info("Call answered: session={} callee={}", sessionId, calleeId);
    }

    /**
     * Relay an ICE candidate from one peer to the other.
     *
     * @param sessionId        active chat session
     * @param senderId         userId sending the candidate
     * @param iceCandidateJson JSON string of the RTCIceCandidate object
     */
    public void relayIceCandidate(String sessionId, Long senderId, String iceCandidateJson) {
        ChatSession session = chatSessionService.getSession(sessionId);
        validateSession(session, sessionId, senderId);

        String senderAnonymousId = session.getUserAnonymousId(senderId);
        ChatMessage ice = ChatMessage.signalingMessage(
                sessionId, senderAnonymousId, ChatMessage.MessageType.CALL_ICE, iceCandidateJson);

        chatMessagingService.relaySignal(sessionId, ice);
        log.debug("ICE candidate relayed: session={} sender={}", sessionId, senderId);
    }

    /**
     * End or reject a call.  Relays CALL_ENDED to the partner and removes the
     * call record.  Safe to call even when no call is active (no-op).
     *
     * @param sessionId  active chat session
     * @param endingUser userId of the user ending or rejecting the call
     */
    public void endCall(String sessionId, Long endingUser) {
        ChatSession session = chatSessionService.getSession(sessionId);
        activeCalls.remove(sessionId);   // remove first so partner can start a new call immediately

        if (session == null) {
            log.warn("endCall: session {} not found — record removed, no relay sent", sessionId);
            return;
        }

        String endingAnonymousId = session.getUserAnonymousId(endingUser);
        ChatMessage ended = ChatMessage.signalingMessage(
                sessionId,
                endingAnonymousId != null ? endingAnonymousId : "SYSTEM",
                ChatMessage.MessageType.CALL_ENDED,
                null);

        chatMessagingService.relaySignal(sessionId, ended);
        log.info("Call ended: session={} endedBy={}", sessionId, endingUser);
    }

    /**
     * Called when a chat session terminates (leave / timeout / force-end) so any
     * in-progress call record is cleaned up.  The partner will receive CALL_ENDED
     * as part of the normal session-end flow; no extra signal is sent here.
     *
     * @param sessionId the session that is ending
     */
    public void handleSessionEnded(String sessionId) {
        if (activeCalls.remove(sessionId) != null) {
            log.info("Cleaned up active call for ended session {}", sessionId);
        }
    }

    /**
     * Safety net: sweep zombie CallRecords left behind by abrupt
     * network drops where CALL_ENDED was never sent.
     * Runs every 2 minutes; evicts any call older than 30 minutes.
     */
    @Scheduled(fixedRate = 120_000)
    public void sweepStaleCalls() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(30));
        int before = activeCalls.size();
        activeCalls.entrySet().removeIf(e -> e.getValue().startedAt.isBefore(threshold));
        int removed = before - activeCalls.size();
        if (removed > 0) {
            log.info("sweepStaleCalls: evicted {} zombie call record(s)", removed);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateSession(ChatSession session, String sessionId, Long userId) {
        if (session == null)          throw new RuntimeException("Session not found: " + sessionId);
        if (!session.isActive())      throw new RuntimeException("Session is not active: " + sessionId);
        if (!session.hasUser(userId)) throw new RuntimeException("User not part of session: " + sessionId);
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private static class CallRecord {
        final String   sessionId;
        final Long     initiatorId;
        final CallType callType;
        final Instant  startedAt;

        CallRecord(String sessionId, Long initiatorId, CallType callType) {
            this.sessionId   = sessionId;
            this.initiatorId = initiatorId;
            this.callType    = callType;
            this.startedAt   = Instant.now();
        }
    }
}
