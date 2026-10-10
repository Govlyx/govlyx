# API Documentation

This document provides a comprehensive overview of the APIs currently utilized by the frontend (`Govlyx`), mapping them to their corresponding backend controllers and Data Transfer Objects (DTOs) in the `AI` Spring Boot application. It includes required fields for Postman testing and a brief description of each API's functionality.

> [!TIP]
> This documentation is intended for engineers to quickly understand the API contract between the frontend and backend, and to easily set up Postman requests for testing.

---

## 1. Authentication Service (`authService.ts`)

### Citizen Registration
- **Endpoint:** `POST /api/auth/register/citizen`
- **Short Description:** Registers a new citizen user.
- **Backend DTO:** `RegisterRequest`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!",
    "username": "citizen_user",
    "pincode": "110001",
    "isAdult": true
  }
  ```

### Department Registration
- **Endpoint:** `POST /api/auth/register/department`
- **Short Description:** Registers a new department user.
- **Backend DTO:** `RegisterRequest`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "email": "dept@example.com",
    "password": "Password123!",
    "username": "dept_user",
    "pincode": "110001",
    "isAdult": true
  }
  ```

### User Login
- **Endpoint:** `POST /api/auth/login`
- **Short Description:** Authenticates a user and returns a token.
- **Backend DTO:** `AuthRequest`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!"
  }
  ```

### Verify Email
- **Endpoint:** `GET /api/auth/verify-email?token={token}`
- **Short Description:** Verifies the user's email address using a token.
- **Postman Test Fields:** Query Parameter `token`.

### Resend Verification
- **Endpoint:** `POST /api/auth/resend-verification?email={email}`
- **Short Description:** Resends the email verification link.
- **Postman Test Fields:** Query Parameter `email`.

---

## 2. Post Service (`postService.ts`)

### Create Issue Post
- **Endpoint:** `POST /api/posts`
- **Short Description:** Creates a standard issue/civic post.
- **Backend DTO:** `PostCreateDto`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "content": "Pothole on Main St",
    "targetPincode": "110001",
    "forceSubmit": false
  }
  ```
> [!NOTE]
> Includes `Idempotency-Key` in headers if applicable.

### Create Issue Post with Media
- **Endpoint:** `POST /api/posts/with-media`
- **Short Description:** Creates an issue post with file attachments.
- **Backend DTO:** `PostCreateDto` (as form data)
- **Postman Test Fields (Multipart/Form-Data):**
  - `content` (Text): "Issue description"
  - `targetPincode` (Text): "110001"
  - `media` (File): [Select File]
  - `forceSubmit` (Text): "false"

### Create Social Post
- **Endpoint:** `POST /api/social-posts/text`
- **Short Description:** Creates a general social post or community post.
- **Backend DTO:** `SocialPostCreateDto`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "content": "Hello community!",
    "allowComments": true,
    "communityId": null 
  }
  ```

### Create Social Post with Media
- **Endpoint:** `POST /api/social-posts/with-media`
- **Short Description:** Creates a social post with file attachments.
- **Backend DTO:** `SocialPostCreateDto` (as Blob)
- **Postman Test Fields (Multipart/Form-Data):**
  - `post` (Text - application/json blob): `{"content":"Hello","allowComments":true}`
  - `media` (File): [Select File]

### Like/Save Post
- **Endpoints:** 
  - `POST /api/interactions/{postType}/{postId}/like`
  - `POST /api/interactions/{postType}/{postId}/save`
- **Short Description:** Toggles the like or save status of a post or social-post.
- **Postman Test Fields:** Path variables `{postType}` (either `posts` or `social-posts`) and `{postId}`.

### Resolve Issue
- **Endpoint:** `POST /api/posts/{postId}/resolution?isResolved=true&updateMessage={message}`
- **Short Description:** Marks an issue post as resolved.
- **Postman Test Fields:** Path variable `{postId}` and Query Parameters `isResolved`, `updateMessage`.

---

## 3. Community Service (`communityService.ts`)

### Get Communities
- **Endpoint:** `GET /api/communities`
- **Short Description:** Fetches a list of communities (with optional filter parameters).
- **Postman Test Fields:** Query Parameters (optional, e.g., `page`, `size`).

### Join / Leave Community
- **Endpoints:**
  - `POST /api/communities/{id}/join`
  - `POST /api/communities/{id}/leave`
- **Short Description:** Join or leave a specific community by ID.
- **Postman Test Fields:** Path variable `{id}`.

### Handle Join Request
- **Endpoint:** `POST /api/communities/{communityId}/join-requests/{requestId}/approve` (or `/reject`)
- **Short Description:** Approves or rejects a user's request to join a private community.
- **Postman Test Fields:** Path variables `{communityId}` and `{requestId}`.

### Create Comment
- **Endpoint:** `POST /api/comments/{postType}/{postId}`
- **Short Description:** Adds a comment or a reply to an existing comment.
- **Backend DTO:** `CommentCreateDto`
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "text": "This is a comment",
    "parentCommentId": null 
  }
  ```

---

## 4. Chat & Messaging (`chatApi.service.ts`)

### Search / Matchmaking
- **Endpoint:** `POST /api/chat/search`
- **Short Description:** Enters the user into the stranger matchmaking queue.
- **Postman Test Fields:** Empty POST body.

### Send Chat Media
- **Endpoint:** `POST /api/chat/{sessionId}/media`
- **Short Description:** Sends rich media (image, video) to a stranger chat session.
- **Postman Test Fields (Multipart/Form-Data):**
  - `file` (File): [Select File]
  - `type` (Text): "image" (or "video", etc)
  - `viewTimer` (Text): "5" (optional)
  - `viewOnce` (Text): "true" (optional)

### Send Community Message
- **Endpoint:** `POST /api/communities/{communityId}/chat/messages`
- **Short Description:** Sends a real-time message to a community chat.
- **Backend DTO:** Payload containing content and optional reply fields.
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "content": "Hello community chat!",
    "replyToId": null,
    "sharedPostId": null
  }
  ```

---

## 5. Search Service (`searchService.ts`)

### Quick Search (Autocomplete)
- **Endpoint:** `GET /api/search/quick?q={query}`
- **Short Description:** Returns lightweight search results for typeahead dropdowns.
- **Postman Test Fields:** Query Parameter `q`.

### Infinite / Filtered Search
- **Endpoints:** 
  - `GET /api/search` (All)
  - `GET /api/search/type` (Filtered by type)
- **Short Description:** Returns paginated search results for posts, communities, users, etc.
- **Postman Test Fields:** Query Parameters `q`, `page`, `limit`, `type`.

---

## 6. Department Service (`departmentService.ts`)

### Create Broadcast
- **Endpoint:** `POST /api/posts/broadcast`
- **Short Description:** Creates a JSON broadcast announcement for departments.
- **Backend DTO:** `BroadcastCreateDto` mapped to Request Parameters and Body.
- **Postman Test Fields (JSON Body & Query Params):**
  - **Body:** `{"content": "Broadcast message"}`
  - **Query Params:** `broadcastScope`, `targetCountry`, `targetStates`, `targetDistricts`, `targetPincodes`.

---

## 7. Billing Service (`billing.ts`)

### Create Order
- **Endpoint:** `POST /api/billing/create-order`
- **Short Description:** Initiates a Razorpay order for upgrading tiers.
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "targetTier": "GOVLYX_PRO",
    "billingCycle": "MONTHLY"
  }
  ```

### Verify Payment
- **Endpoint:** `POST /api/billing/verify`
- **Short Description:** Verifies the Razorpay payment signature.
- **Postman Test Fields (JSON Body):**
  ```json
  {
    "razorpay_order_id": "order_xyz",
    "razorpay_payment_id": "pay_xyz",
    "razorpay_signature": "signature_hash"
  }
  ```
