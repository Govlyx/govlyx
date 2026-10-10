# Database Schema & Performance Indexing Documentation

This document describes the logical database schema derived from the application's JPA entities and the indexing strategy applied via [schema_indexes.sql](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/resources/schema_indexes.sql) to optimize the application's API endpoints.

---

## 📂 Logical Database Schema

The system uses a relational PostgreSQL database structure mapped by Hibernate (`spring.jpa.hibernate.ddl-auto=update`). Below is a mapping of the primary tables and their relationships:

| Table Name | Entity Class | Primary Key | Key Relationships / Fields |
|---|---|---|---|
| `users` | [User.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/User.java) | `id` | Fields: `username` (unique), `email` (unique), `password`, `is_active` |
| `social_posts` | [SocialPost.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/SocialPost.java) | `id` | Relationships: `user_id` (Author), `community_id` (Optional)<br>Fields: `pincode`, `district_prefix`, `state_prefix`, `status`, `virality_score`, `engagement_score`, `is_flagged` |
| `posts` | [Post.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/Post.java) | `id` | Relationships: `user_id` (Author)<br>Fields: `status`, `created_at` (Broadcast/official issues) |
| `notifications` | [Notification.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/Notification.java) | `id` | Relationships: `user_id` (Recipient)<br>Fields: `is_read`, `created_at` |
| `comments` | [Comment.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/Comment.java) | `id` | Relationships: `user_id`, `social_post_id` (optional), `post_id` (optional) |
| `communities` | [Community.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/Community.java) | `id` | Fields: `created_at` |
| `post_likes` | [PostLike.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/PostLike.java) | `id` | Relationships: `user_id`, `post_id` (optional), `social_post_id` (optional)<br>Fields: `reaction_type` |
| `saved_posts` | [SavedPost.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/SavedPost.java) | `id` | Relationships: `user_id`, `post_id` (optional), `social_post_id` (optional) |
| `post_views` | [PostView.java](file:///C:/Users/Madhav/Desktop/Springboot%20project/AI/AI/src/main/java/com/JanSahayak/AI/model/PostView.java) | `id` | Relationships: `user_id`, `post_id` (optional), `social_post_id` (optional)<br>Fields: `viewed_at` |

---

## ⚡ Indexing Optimization Strategy

All indexes are defined using `CONCURRENTLY` so they can be generated on a live production database without taking locks or interrupting active transactions.

### 1. Social Posts Feed (Hot Paths)
These index queries power the main social feeds (Hot, Top, New, and cursor paginated feeds):
* **`idx_social_post_virality`**
  - **Definition**: `ON social_posts (status, virality_score DESC, created_at DESC) WHERE is_flagged = false`
  - **Optimizes**: The "Hot Feed" sorting by virality score for active, unflagged posts.
* **`idx_social_post_engagement`**
  - **Definition**: `ON social_posts (status, engagement_score DESC, created_at DESC) WHERE is_flagged = false`
  - **Optimizes**: The "Top Feed" sorting by engagement score.
* **`idx_social_post_status_created`**
  - **Definition**: `ON social_posts (status, created_at DESC)`
  - **Optimizes**: Chronological feed querying ("New Feed").
* **`idx_social_post_status_id_desc`**
  - **Definition**: `ON social_posts (status, id DESC)`
  - **Optimizes**: Stable pagination cursors.

### 2. Geographical Waterfall Feeds
Optimizes location-based lookup feeds using hierarchical fallbacks:
* **`idx_social_post_pincode`**: `ON social_posts (pincode, status, created_at DESC) WHERE pincode IS NOT NULL`
* **`idx_social_post_district_prefix`**: `ON social_posts (district_prefix, status, created_at DESC) WHERE district_prefix IS NOT NULL`
* **`idx_social_post_state_prefix`**: `ON social_posts (state_prefix, status, created_at DESC) WHERE state_prefix IS NOT NULL`

### 3. Communities
* **`idx_social_post_community_id`**: `ON social_posts (community_id, status, created_at DESC) WHERE community_id IS NOT NULL`
  - **Optimizes**: Paginated feeds inside specific community channels.
* **`idx_communities_created_at`**: `ON communities (created_at DESC)`
  - **Optimizes**: Browsing and listing recently created communities.

### 4. Broadcast/Official Issue Feeds
* **`idx_posts_status_created`**: `ON posts (status, created_at DESC)`
* **`idx_posts_status_id_desc`**: `ON posts (status, id DESC)`
* **`idx_posts_user_id_created`**: `ON posts (user_id, created_at DESC)`
  - **Optimizes**: User profile pages displaying their official posts.

### 5. Polled Notifications
* **`idx_notifications_user_read`**
  - **Definition**: `ON notifications (user_id, is_read, created_at DESC)`
  - **Optimizes**: The `GET /api/notifications/unread/count` polling endpoint, which hits the DB every 60 seconds from every active user session.
* **`idx_notifications_user_id_desc`**
  - **Definition**: `ON notifications (user_id, id DESC)`
  - **Optimizes**: Paginated list views of notifications.

### 6. User Lookup & Identity
* **`idx_users_username_lower`**: `ON users (LOWER(username) text_pattern_ops)`
  - **Optimizes**: Prefix searching for usernames (e.g. `@mentions` search, admin lookup) by applying case-insensitive matching.
* **`idx_users_email`**: `ON users (LOWER(email))`
  - **Optimizes**: Authentication/login lookups.

### 7. Interaction States (Bulk Checks)
* **`idx_post_like_social_post_user`** and **`idx_post_like_post_user`**
  - **Optimizes**: Batch checks verifying if the logged-in user has liked/interacted with the visible list of posts.
* **`idx_saved_post_user_social`** and **`idx_saved_post_user_post`**
  - **Optimizes**: Batch checks verifying which posts in the feed are saved by the current user.

### 8. View Deduplication & Comments
* **`idx_post_view_social_post_user_time`** and **`idx_post_view_post_user_time`**
  - **Optimizes**: Deduplication windows checked by the view tracker to prevent repetitive view count increments.
* **`idx_comments_social_post_created`**, **`idx_comments_post_created`**, and **`idx_comments_user_created`**
  - **Optimizes**: Comment section listing (chronological tree rendering) and user activity tracking.
