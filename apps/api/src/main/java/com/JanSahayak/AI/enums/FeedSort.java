package com.JanSahayak.AI.enums;

/**
 * FeedSort — the three sort modes available on the All, Location, and Following tabs.
 *
 * Applied via {@code ?sort=} query param on browse-tab endpoints.
 * Not used on the For You tab — HLIG ML owns ranking there.
 *
 * <pre>
 *   HOT  → sorted by viralityScore DESC, window = last 72 hours   [default]
 *   NEW  → sorted by createdAt DESC (pure chronological)
 *   TOP  → sorted by engagementScore DESC, all-time
 * </pre>
 *
 * Usage:
 * <pre>
 *   GET /api/v1/feed/all?sort=HOT
 *   GET /api/v1/feed/location?sort=NEW&lastPostId=123
 *   GET /api/v1/feed/following?sort=TOP
 * </pre>
 */
public enum FeedSort {

    /** Most viral in the last 72 hours — sorted by viralityScore DESC. */
    HOT,

    /** Most recent — sorted by createdAt DESC. */
    NEW,

    /** All-time highest engagement — sorted by engagementScore DESC. */
    TOP;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static FeedSort fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return HOT;
        }
        String upper = value.trim().toUpperCase();
        if ("RECENT".equals(upper) || "LATEST".equals(upper)) {
            return NEW;
        }
        try {
            return FeedSort.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return HOT;
        }
    }
}
