# Report + Auto-Block + Appeal — COMPLETE FINAL PLAN
# Saved: 2026-07-28 | Author: Senior Dev Planning Session

---

## SYSTEM 1: 1v1 Anonymous Chat — Chat Trust Score (Telegram + TikTok Hybrid)

### Score Zones (0–100)
Every user has a `chatTrustScore`. New users start at **70** (not 100 — monitored per TikTok standard).

```
100–71  ✅ Green  — Full Access
70–51   ⚠️ Yellow — Monitored (silent, no restriction)
50–31   🔶 Orange — Restricted (+30s match wait, warning shown)
30–0    🔴 Red    — Escalating Time Ban (see below)
```

### Escalating Ban Tiers (NO Permanent Bans)
| Offense # | Ban Duration |
|---|---|
| 1st | **6 hours** |
| 2nd | **12 hours** |
| 3rd | **24 hours** |
| 4th | **72 hours** |
| 5th | **7 days (168h)** |
| 6th+ | **14 days (336h)** — repeats at this tier |

After every ban served → **+5 score recovery bonus** to give a fresh start.
If user re-offends, they advance to the next tier automatically.

### Score Changes

**Score INCREASES (Good Behavior):**
| Event | Points |
|---|---|
| Session lasted > 5 minutes | +3 |
| Session lasted > 15 minutes | +5 |
| Both disconnected amicably (no report filed) | +2 |
| 24 hours pass with no negative event | +1 |
| After serving a ban | +5 (fresh start bonus) |
| Appeal approved by admin | +10 |

**Score DECREASES — Report-Based (category-weighted):**
| Category | Penalty |
|---|---|
| HARASSMENT, OBSCENITY (Emergency IT Rules) | -30 pts + human review flag |
| HATE_SPEECH, IMPERSONATION | -20 pts |
| SPAM, MISINFORMATION | -10 pts |
| OTHER | -5 pts |

**Score DECREASES — Behavioral Signals (no report needed, TikTok-style):**
| Signal | Penalty |
|---|---|
| Partner disconnects within 30 seconds of match | -3 pts |
| Partner disconnects within 10 seconds | -8 pts |
| Disconnect rate > 60% across last 5 sessions | -10 pts (one-time) |

**Anti-Abuse (Telegram coordinated attack detection):**
Reports arriving from accounts with trustScore < 50 within 60 seconds of each other
for the same target → silently discarded. Reporters flagged internally.

---

## SYSTEM 2: Appeal Flow (Banned User → Platform Admin)

### Complete Appeal Lifecycle
```
Step 1: Banned user sees ban screen with live countdown timer
Step 2: User clicks "📧 Appeal to Govlyx Safety"
Step 3: Appeal modal opens (1 active appeal per suspension only)
Step 4: User fills explanation (ban details auto-filled, read-only)
Step 5: ChatAppeal saved as PENDING → admin notified via notification
Step 6: "⏳ Appeal Under Review" replaces button — locked until decision
Step 7: Admin reviews in AdminDashboard "Appeals" tab
Step 8: Admin selects: Approve / Reject / Reject+Extend
Step 9: In-app notification sent to user with admin's note
Step 10:
  If APPROVED → ban lifted immediately + trust score +10
  If REJECTED → ban continues, user sees reason
  If REJECTED_EXTENDED → next ban tier applied immediately
```

### Ban Screen UI States

**State 1 — Banned, No Appeal Submitted:**
```
🚫 Quick Chat Suspended
You can chat again in: [11h : 43m : 22s]  ← live countdown
Reason: Harassment report received
Offense #: 2 of 6 tiers
Trust Score: ██████░░░░  42/100  (recovers automatically)
[ 📧 Appeal to Govlyx Safety ]
```

**State 2 — Appeal Modal:**
```
📧 Appeal Your Suspension
─────────────────────────────────
Auto-filled (read-only):
• Suspension: 12 hours (Offense #2)
• Reason: Harassment report received
• Trust Score: 42/100

Your explanation: * (max 500 chars)
[ textarea ]

ℹ️ Appeals reviewed within 48 hours.
   One appeal per suspension.
   Identity visible to Safety Team only.

[ Submit Appeal ]  [ Cancel ]
```

**State 3 — Appeal Pending:**
```
🚫 Quick Chat Suspended
[  11h : 43m : 22s  ]
⏳ Appeal Under Review
   Submitted: 2 hours ago
   Expected response: within 48h
```

**State 4a — Approved (In-App Notification):**
```
✅ Your appeal has been approved.
Quick Chat access restored. Trust Score +10.
Note: "[admin's note]"
[ Start Chatting Now ]
```

**State 4b — Rejected (In-App Notification):**
```
❌ Your appeal has been rejected.
Suspension remains. You can chat again in: [timer]
Note: "[admin's note]"
[ View Guidelines ]
```

### Admin Appeal Card (AdminDashboard — "Appeals" Tab)
Each card shows:
- User info (username, ID)
- Trust Score, Offense # of 6 tiers
- Current ban: duration + expiry time
- Ban reason
- User's explanation (full text)
- Account history: total sessions, avg duration, previous offenses
- Admin note input (optional)
- 3 action buttons: ✅ Approve | ❌ Reject | 🔴 Reject + Extend

---

## SYSTEM 3: Community Chat — Reported Message Visibility

### 3-State Message System

| State | Trigger | Regular Users | Reporter | Admin |
|---|---|---|---|---|
| **FLAGGED** | 1–2 unique reports | Full message (no change) | Dimmed + "⏳ Under review" | Full + 🚩 flag + [Review] |
| **SUPPRESSED** | 3+ unique reports (auto) | ⚠️ Blurred + "Tap to view" | Same as others | Full + 🚩 + [Review] |
| **REMOVED** | Admin manually deletes | 🗑️ "Removed by a moderator" | Same | Shows in audit history |

**Key Rules:**
- NEVER auto-delete on report — only blur (prevents false-report abuse on civic speech)
- Only admin human decision causes permanent REMOVED state
- REMOVED shows a tombstone placeholder — full transparency

### Community Mute Thresholds (Unique Reporters Only)
| Unique Reports | Auto-Action | Who's Notified |
|---|---|---|
| **3** | Auto-mute sender 1 hour | Community Admin (silent) |
| **5** | Auto-mute sender 24 hours | Community Admin (alert) |
| **10** | Auto-mute sender Permanent | Community Admin + Platform Admin |

Same person reporting multiple times = still counts as 1 unique reporter.
Coordinated mass reports = Telegram-style detection → discarded.

---

## Files to Change — 16-Step Execution Order

### STEP 1 — [MODIFY] User.java
New fields for 1v1 chat suspension:
```java
@Column(name = "chat_trust_score")
private Integer chatTrustScore = 70;

@Column(name = "quick_chat_suspension_count")
private Integer quickChatSuspensionCount = 0;

@Column(name = "is_quick_chat_suspended", columnDefinition = "boolean default false")
private Boolean isQuickChatSuspended = false;

@Column(name = "quick_chat_suspended_until")
@Temporal(TemporalType.TIMESTAMP)
private Date quickChatSuspendedUntil;

@Column(name = "chat_suspend_reason", length = 255)
private String chatSuspendReason;
```

### STEP 2 — [MODIFY] CommunityMember.java
New fields for community mute:
```java
@Column(name = "is_muted", columnDefinition = "boolean default false")
private Boolean isMuted = false;

@Column(name = "muted_until")
@Temporal(TemporalType.TIMESTAMP)
private Date mutedUntil; // null = permanent

@Column(name = "mute_reason", length = 255)
private String muteReason;

@Column(name = "report_strike_count")
private Integer reportStrikeCount = 0;
```

### STEP 3 — [MODIFY] CommunityMessage.java
New fields for 3-state visibility:
```java
public enum MessageReportStatus { NONE, FLAGGED, SUPPRESSED, REMOVED }

@Enumerated(EnumType.STRING)
@Column(name = "report_status")
@Builder.Default
private MessageReportStatus reportStatus = MessageReportStatus.NONE;

@Column(name = "report_count")
@Builder.Default
private Integer reportCount = 0;

@Column(name = "removed_by_admin_at")
@Temporal(TemporalType.TIMESTAMP)
private Date removedByAdminAt;

@Column(name = "removed_reason", length = 255)
private String removedReason;
```

### STEP 4 — [NEW] ChatAppeal.java (Model)
```java
@Entity
@Table(name = "chat_appeals")
public class ChatAppeal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "appeal_message", length = 500, nullable = false)
    private String appealMessage;

    @Column(name = "ban_duration_hours")
    private Integer banDurationHours;

    @Column(name = "trust_score_at_appeal")
    private Integer trustScoreAtAppeal;

    @Column(name = "suspension_count")
    private Integer suspensionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AppealStatus status = AppealStatus.PENDING;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    @Builder.Default
    private Date submittedAt = new Date();

    @Column(name = "reviewed_at")
    private Date reviewedAt;

    public enum AppealStatus { PENDING, APPROVED, REJECTED, REJECTED_EXTENDED }
}
```

### STEP 5 — [NEW] ChatAppealRepository.java
```java
public interface ChatAppealRepository extends JpaRepository<ChatAppeal, Long> {
    boolean existsByUser_IdAndStatus(Long userId, AppealStatus status);
    Page<ChatAppeal> findByStatusOrderBySubmittedAtAsc(AppealStatus status, Pageable pageable);
    Page<ChatAppeal> findAllByOrderBySubmittedAtDesc(Pageable pageable);
}
```

### STEP 6 — [MODIFY] ContentReportRepository.java
```java
@Query("SELECT COUNT(DISTINCT r.reporter.id) FROM ContentReport r WHERE r.targetType = :type AND r.targetId = :id")
long countDistinctReportersByTargetTypeAndTargetId(String type, Long id);

List<ContentReport> findByTargetTypeAndTargetIdAndCreatedAtAfter(String type, Long id, Date after);
```

### STEP 7 — [NEW] ChatTrustScoreService.java
Core scoring engine:
- BAN_HOURS = {6, 12, 24, 72, 168, 336, 336}
- `applyReportPenalty(Long targetUserId, ReportCategory category)`
- `recordSessionSignals(String sessionId, Long userId, long durationSecs, boolean partnerLeftEarly)`
- `getQuickChatStatus(User user)` → returns ChatStatusDto for ban screen
- `assertQuickChatAccess(User user)` → throws SecurityException if banned
- `adjustScore(User user, int delta, String reason)` → internal, applies ban tiers
- `isCoordinatedAttack(Long targetId, String type)` → returns true if burst reports detected
- `@Scheduled dailyRecovery()` → 3 AM daily: +1 recovery, auto-lift expired bans

### STEP 8 — [NEW] ChatAppealService.java
- `submitAppeal(Long userId, String message)` → validates 1 per ban, saves, notifies admin
- `reviewAppeal(Long appealId, String decision, String adminNotes, User admin)`
  - APPROVED → lift ban + adjustScore +10 + notify user
  - REJECTED → notify user with reason
  - REJECTED_EXTENDED → applyNextBanTier + notify user
- `hasPendingAppeal(Long userId)` → boolean for ban screen UI

### STEP 9 — [MODIFY] ChatSessionService.java
- In `findMatch()`: call `chatTrustScoreService.assertQuickChatAccess(user)`
- Orange Zone (score 31–50): inject 30s artificial delay before match
- On session end: call `chatTrustScoreService.recordSessionSignals()`

### STEP 10 — [MODIFY] ChatController.java
New REST endpoints:
```
GET  /api/chat/status         → ban screen data (suspended, timer, trustScore, hasPendingAppeal)
POST /api/chat/report         → file report during session (triggers trust score penalty)
POST /api/chat/appeal         → submit suspension appeal
GET  /api/chat/appeal/status  → check pending appeal status
```

### STEP 11 — [MODIFY] ContentReportController.java
New admin appeal endpoints:
```
GET /api/reports/admin/appeals?status=PENDING
GET /api/reports/admin/appeals/all
PUT /api/reports/admin/appeals/{id}/review
    Body: { "decision": "APPROVED|REJECTED|REJECTED_EXTENDED", "adminNotes": "..." }
```

### STEP 12 — [MODIFY] CommunityChatService.java
- `reportMessage()` → unique reporter count → update MessageReportStatus + mute thresholds
- `processNewMessage()` → check mute before allowing (auto-lift expired mutes)
- `getCommunityReports(communityId, adminUserId)` → for admin Reports tab
- `muteUser(communityId, targetUserId, requestingUserId, durationHours)` → admin action

### STEP 13 — [MODIFY] CommunityChatController.java
New endpoints:
```
GET    /api/communities/{id}/chat/reports
POST   /api/communities/{id}/members/{uid}/mute    Body: { "durationHours": 24 }
DELETE /api/communities/{id}/members/{uid}/mute
```

### STEP 14 — [MODIFY] StrangerChat.tsx
- `GET /api/chat/status` on mount → render ban screen if suspended
- 4 ban screen states (no appeal / appeal modal / pending / notification handled)
- Live countdown timer using setInterval
- Timer hits 0 → re-fetch status → "Welcome back!" if clear
- AlertTriangle icon in header → report modal when session active
- Report modal: category select + description + "Also skip" checkbox

### STEP 15 — [MODIFY] CommunityChat.tsx
- 🚩 "Report Message" in MoreVertical context menu (other users' messages only)
- Inline report modal: category + description + footer disclaimer
- Message rendering by reportStatus:
  - NONE/FLAGGED → reporter sees dimmed badge; others see normal (admin sees 🚩)
  - SUPPRESSED → blurred card + "Tap to view" toggle for everyone
  - REMOVED → "🗑️ Removed by a moderator" tombstone for everyone
- Admin: 🚩 badge + [Review Report] button inline on FLAGGED/SUPPRESSED messages
- Mute error handling in send: show "⛔ You are muted in this community"

### STEP 16 — [MODIFY] AdminDashboard.tsx
- New "Appeals" tab (fetch PENDING appeals, admin approve/reject/extend)
- 3-line fix: CHAT_SESSION + COMMUNITY_CHAT_MESSAGE targetType → show description directly
- Trust Score column in user management table

### STEP 17 — [MODIFY] Communities.tsx
- "Reports" tab — visible to admin/owner/moderator only
- Report cards with: content, sender, category badge, time, mute actions, dismiss

---

## Database Schema Notes

### New Table: chat_appeals
```sql
CREATE TABLE chat_appeals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    appeal_message VARCHAR(500) NOT NULL,
    ban_duration_hours INT,
    trust_score_at_appeal INT,
    suspension_count INT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    admin_notes VARCHAR(500),
    reviewed_by_id BIGINT,
    submitted_at DATETIME NOT NULL,
    reviewed_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (reviewed_by_id) REFERENCES users(id),
    INDEX idx_appeal_status (status),
    INDEX idx_appeal_user (user_id)
);
```

### Modified Table: users
New columns:
- `chat_trust_score` INT DEFAULT 70
- `quick_chat_suspension_count` INT DEFAULT 0
- `is_quick_chat_suspended` BOOLEAN DEFAULT false
- `quick_chat_suspended_until` DATETIME
- `chat_suspend_reason` VARCHAR(255)

### Modified Table: community_members
New columns:
- `is_muted` BOOLEAN DEFAULT false
- `muted_until` DATETIME
- `mute_reason` VARCHAR(255)
- `report_strike_count` INT DEFAULT 0

### Modified Table: community_messages
New columns:
- `report_status` VARCHAR(20) DEFAULT 'NONE'
- `report_count` INT DEFAULT 0
- `removed_by_admin_at` DATETIME
- `removed_reason` VARCHAR(255)

---

## Key Architecture Decisions (Summary)

1. **Report message, not user** — evidence-anchored reporting (Discord/TikTok standard)
2. **Trust Score not strike counter** — gradual, fair, recoverable (TikTok CHR)
3. **Escalating bans, no permanent bans** — 6h → 12h → 24h → 72h → 7d → 14d
4. **Coordinated attack detection** — burst reports discarded (Telegram standard)
5. **1 appeal per suspension** — prevents appeal spam
6. **3 admin decisions** — Approve / Reject / Reject+Extend
7. **3-state message visibility** — FLAGGED / SUPPRESSED / REMOVED (never auto-delete)
8. **Community admin gets Reports tab** — two-tier moderation (community + platform)
9. **Ban screen with live countdown** — UX transparency (Telegram SpamBot-style)
10. **Behavioral signals without reports** — early disconnect rate tracked (TikTok-style)
