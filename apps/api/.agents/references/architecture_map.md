# Jan-Sahayak-AI Architecture Map

## Overview
Jan-Sahayak-AI is a Spring Boot application acting as a platform for citizen grievances, government civic broadcasting, and community interaction. It features citizen-to-citizen anonymous chatting, community management, post creation/resolution, HLIG hyper-local feed ranking, real-time messaging, and SLA monitoring.

## Core Domain Models
- `User`: Platform users with roles (`ROLE_USER`, `ROLE_DEPARTMENT`, `ROLE_ADMIN`). Supports OAuth2 (`googleId`, `authProvider`), geocoding/pincodes, preferences (e.g. `theme` with light/dark persistence).
- `Post`: Civic issue reports and official government/department broadcasts. Supports multi-level geographic targeting (`AREA`, `DISTRICT`, `STATE`, `COUNTRY`), resolution tracking, and denormalized engagement counters (`likeCount`, `dislikeCount`, `commentCount`, `shareCount`, `viewCount`, `saveCount`).
- `SocialPost`: Public social discussions and localized citizen posts. Includes category, hashtag indexing, virality/engagement scoring, and embedded polling.
- `Community`: Geographic or topical communities with member roles, privacy levels, and dynamic health scores.
- `CommunityMember`: Membership mappings with composite roles.
- `Poll`, `PollOption`, `PollVote`: Dynamic polling attached to social posts with flexible durations (1H, 1D, 3D, 7D, Always/indefinite), multiple-vote configuration, and vote percentage analytics.
- `PostShare`: Denormalized and breakdown-tracked sharing records for posts and social posts with share types (`LINK_COPY`, `EXTERNAL_SHARE`).
- `Comment` & `CommentInteraction`: Hierarchical multi-tier comments with parent-reply threading, upvotes/downvotes, and atomic cascade counter decrements.
- `ChatSession` & `ChatSessionAudit`: Ephemeral 1-vs-1 chat session tracking and persistent audit logging. Audit logging is selectively bypassed for test/synthetic accounts matching configured regex patterns (e.g., `^(user\d+|usersomenumer)@gmail\.com$`).
- `PostTranslation`: Cached multi-lingual translations for posts and social content.
- `Notification`: In-app notification delivery across civic broadcasts, community invitations, join requests, comment replies, post interactions, and role updates. Supports unread tracking, cursor pagination, and real-time delivery via WebSocket/WebPush.
- `NotificationSummaryDto`: Compact projection DTO used for instant single-roundtrip tab count calculations.

## Key Services & Controllers
- `NotificationController` & `NotificationService`: Real-time notification aggregation and cursor-paginated delivery with server-side category tab filtering (`all`, `unread`, `invites`, `interactions`), single-roundtrip aggregate summary count projections (`GET /api/notifications/counts`), batch join-request prefetching to eliminate N+1 queries, and bulk database state updates (`markAllAsReadForUser`).
- `FeedController` & `HLIGFeedService`: High-performance multi-tier feed recommendation system (FOR_YOU, LOCATION, FOLLOWING, OFFICIAL, NEIGHBORHOOD_QA) with Caffeine candidate caching (`hlig_feed`), phase-based scoring, diversity injection, and full multi-scope sort support (`HOT`, `NEW`, `TOP`) across NEARBY, AREA, CITY, and DISTRICT scopes.
- `PostService` & `SocialPostService`: Core CRUD, batch DTO conversions, batch interaction checks, Cloudinary media processing, and Neighborhood Q&A 4-layer geo-scoping with auto-coordinate resolution from `PincodeLookup` and cursor pagination.
- `PostInteractionService` & `CommentService`: Real-time post interactions with atomic counter database updates, Spring cache management (`postCounts`), and STOMP WebSocket broadcast updates (`/topic/feed.updates`, `/topic/post.{postId}.updates`, `/topic/community.{id}.updates`).
- `PollService`: Poll lifecycle management, flexible duration calculations (including perpetual/Always voting), option management, and batch vote recording.
- `TranslationService`: Multi-lingual translation with non-blocking virtual threads, 300ms feed timeout guard, and DB/API tier caching (`translationsApi`).
- `PinCodeLookupService`: Geocoding and geographic radius calculations with Caffeine caching (`pincode_nearby`, `pincode-data`).
- `UserTaggingService`: Mentions and user tagging with batch lookup optimization.
- `ChatSessionService`: WebSocket routing, ephemeral media lifecycle, and selective audit persistence filtering with configurable regex patterns (`chat.audit.excluded-email-regex`).
- `MatchmakingService`: Location-aware stranger chat matching.
- `AuthController` & `GoogleOAuthConfig`: High-performance OAuth2 authentication pipeline with singleton in-memory `GoogleIdTokenVerifier` certificate caching, zero redundant DB round-trips, direct UserDetails JWT generation, and atomic refresh token rotation via `RefreshTokenService.createRefreshToken(User)`.
- `SearchController` & `SearchService`: Unified multi-type search engine (POST, SOCIAL_POST, COMMUNITY, HASHTAG) supporting cursor/offset pagination, typeahead autocomplete, and tag-aware multi-entity search that surfaces related civic posts, hashtag-indexed social discussions, and matching communities.
- `WebSocketConfig`: Inbound STOMP channel security interceptor with graceful unauthenticated connection filtering, user tracking, and session IP resolution.

## Architectural Patterns & Caching
- **Multi-Level Caffeine Caching**: Managed by `CacheConfig` with `SimpleCacheManager` and `TransactionAwareCacheManagerProxy`:
  - `hlig_feed`: High-throughput feed candidate caching (TTL: 2m).
  - `pincode_nearby`: Haversine distance and adjacent pincodes lookup (TTL: 30m).
  - `pincode-data`: Static geographic reference data (TTL: 24h).
  - `translationsApi`: External API translation cache (TTL: 2h).
  - `postCounts`: Active interaction counts map for single posts and social posts with programmatic eviction on interactions/comments.
  - `authUserDetails`, `user-profiles` (`CACHE_USER_PROFILE`): Profile and session caches evicted on user/theme updates so `/api/users/me` immediately delivers updated preferences.
  - `community-list`, `communities`.
- **Google Public Keys In-Memory Caching**: `GoogleOAuthConfig` configures a thread-safe singleton `GoogleIdTokenVerifier` preventing repetitive outbound HTTPS certificate downloads.
- **Real-Time STOMP Event Broadcasting**: Endpoints emit full `STATS_UPDATE` payloads over WebSocket broker for live multi-client synchronization of likes, dislikes, shares, comments, saves, and views.
- **Non-Blocking External Integrations**: Virtual threads for I/O with strict bounded timeouts (e.g. 300ms) to ensure feed endpoints never block.
- **Batch Querying & Eager Fetching**: Batch loads for author roles, member statuses, tags, and `@EntityGraph` eager loading on `PostLikeRepo`, `SavedPostRepo`, and `CommentRepo` activity lookups to eliminate N+1 queries.
- **User Activity Lookups**: Dedicated paginated activity lookup endpoints in `PostInteractionController` (`/api/interactions/{liked, commented, saved}`) with soft-delete exclusion, chronological creation-date ordering, and accurate `LIKE` vs `DISLIKE` reaction state fidelity.
- **Scheduled Tasks**: Background monitoring for SLAs, community health scores, view count buffering (`ViewCountFlusher`), and metrics.
- **Database**: PostgreSQL with Hibernate spatial/indexing optimizations and database counter reconciliation scripts.

