# Govlyx Monetization Backend Implementation Guide

This document outlines the backend architecture and changes implemented to support the **Govlyx Free**, **Govlyx Pro**, and **Govlyx VIP** subscription tiers.

## 1. Subscription Tiers & Pricing

*   **GOVLYX_FREE**: Default tier for all users.
*   **GOVLYX_PRO**: ₹49/month. Unlocks rich media in chat, unlimited matchmaking, and private community creation (quota of 3).
*   **GOVLYX_VIP**: ₹149/month. Inherits all Pro features. Unlocks priority matchmaking, disappearing messages, message pinning, and a higher private community quota (quota of 5).

## 2. Core Entities & Enums

*   **`PassTier`**: Enum mapping the three tier levels.
*   **`UserPassStatus`**: Enum indicating whether a pass is `ACTIVE` or `EXPIRED`.
*   **`UserPass`**: The primary JPA entity (`user_passes` table). Maps a user to their active subscription tier.
    *   Stores Razorpay Order ID & Payment ID.
    *   Maintains the validity timestamp (`validUntil`).
    *   Maintains the `privateCommunityQuota` balance.

## 3. Razorpay Integration

*   **Dependency**: Added `razorpay-java` SDK to `pom.xml`.
*   **`PaymentService`**:
    *   `createOrder()`: Initializes an order via Razorpay API and creates a pending `UserPass` entry.
    *   `verifySignature()`: Validates the Razorpay webhook/callback signature using HMAC SHA256 and activates the pass if valid.
*   **`PaymentController`**: 
    *   `GET /api/billing/me`: Fetches the user's current active tier.
    *   `POST /api/billing/create-order`: Generates an order ID for frontend checkout.
    *   `POST /api/billing/verify`: Completes the payment handshake.

## 4. Centralized Enforcement Service

*   **`PlanEnforcementService`**: Acts as the gatekeeper for all premium features.
    *   `getUserTier(userId)`: Resolves the current tier, defaulting to `GOVLYX_FREE` if expired/non-existent.
    *   `canSendChatMedia()`: Checks if tier is PRO or VIP.
    *   `canSetDisappearingMessages()`: Checks if tier is VIP.
    *   `enforceDailyMatchmakingLimit()`: Consults `ChatSessionAuditRepo` to count matches in the last 24h. Throws `PlanLimitExceededException` if a FREE user exceeds 3 matches.

## 5. Matchmaking Priority (VIP Feature)

*   **`MatchmakingService`**:
    *   The `waitingQueue` was converted from a standard `ConcurrentLinkedQueue` to a `PriorityBlockingQueue`.
    *   The queue is ordered by **Tier** (`GOVLYX_VIP` > `GOVLYX_PRO` > `GOVLYX_FREE`), and then by **Join Timestamp**.
    *   This guarantees that when a new user joins and the system searches the pool for an eligible partner (e.g., matching pincode), a waiting VIP user will *always* be matched before a waiting Free user.

## 6. Chat & Community Moderation

*   **Rich Media (`ChatController`)**:
    *   The `/api/chat/{sessionId}/media` endpoint intercepts media uploads.
    *   Free users are blocked from sending images, videos, and voice notes.
*   **VIP Group Settings (`CommunityChatService`)**:
    *   **Disappearing Messages**: Setting `chatRetentionDays` > 0 throws an exception unless the user is a VIP.
    *   **Pinned Messages**: `toggleMessagePin` requires VIP status.
*   **Secret Community Freezing (`CommunityChatService`)**:
    *   In `processNewMessage()`, if the target community is `SECRET` and `PlanEnforcementService.isCommunityFrozen()` returns true (i.e., the owner's pass has expired), no one can send messages in that community. The community effectively becomes "read-only" until the owner upgrades again.

## 7. Universally Free Features

The following features were explicitly kept free and available to all users (no premium blocks were added):
*   **Auto-Translate**: All logic within `TranslationService` runs for all users.
*   **Poll Creation**: `PollService` creation flows remain unrestricted.
