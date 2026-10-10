package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.UserTagSuggestionDto;
import com.JanSahayak.AI.dto.UserTagValidationResult;
import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.exception.*;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.UserTag;
import com.JanSahayak.AI.repository.*;
import com.JanSahayak.AI.payload.PaginationUtils;
import com.JanSahayak.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ValidationException;
import java.util.*;
import java.util.stream.Collectors;

import static com.JanSahayak.AI.payload.PostUtility.calculateTimeAgo;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTaggingService {

    private final UserRepo userRepository;
    private final PostRepo postRepository;
    private final UserTagRepo userTagRepository;
    private final PinCodeLookupService pincodeLookupService;
    private final PostLikeRepo postLikeRepo;

    @Transactional
    public void processUserTags(Post post) {
        try {
            PostUtility.validatePost(post);
            log.info("Processing user tags for post ID: {} (status: {})", post.getId(), post.getStatus().getDisplayName());

            List<String> extractedUsernames = PostUtility.extractUserTags(post.getContent());

            if (extractedUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                log.warn("Post {} has {} user tags, which exceeds the maximum of {}. Processing first {} tags.",
                        post.getId(), extractedUsernames.size(), Constant.MAX_TAGS_PER_POST, Constant.MAX_TAGS_PER_POST);
                extractedUsernames = extractedUsernames.subList(0, Constant.MAX_TAGS_PER_POST);
            }

            if (!extractedUsernames.isEmpty()) {
                List<User> validUsers = userRepository.findByUsernameInAndIsActiveTrue(extractedUsernames);
                if (validUsers == null) {
                    validUsers = Collections.emptyList();
                }

                // Clear existing active tags
                List<UserTag> existingTags = userTagRepository.findByPostAndIsActiveTrue(post);
                if (existingTags == null) {
                    existingTags = Collections.emptyList();
                }

                existingTags.forEach(tag -> tag.deactivate(post.getUser(), "Post content updated"));

                if (!existingTags.isEmpty()) {
                    userTagRepository.saveAll(existingTags);
                }

                // Create new UserTag entities with location awareness using pincode prefix logic
                List<UserTag> newTags = new ArrayList<>();
                for (User user : validUsers) {
                    String content = post.getContent();
                    String username = "@" + user.getActualUsername();
                    int startPos = content.indexOf(username);

                    if (startPos != -1) {
                        UserTag userTag = UserTag.builder()
                                .post(post)
                                .taggedUser(user)
                                .taggedBy(post.getUser())
                                .isActive(true)
                                .taggedAt(new Date())
                                .build();

                        newTags.add(userTag);
                    }
                }

                // Save new tags
                if (!newTags.isEmpty()) {
                    userTagRepository.saveAll(newTags);
                    log.info("Created {} user tags for post ID: {} (status: {})",
                            newTags.size(), post.getId(), post.getStatus().getDisplayName());
                }
            }
        } catch (jakarta.validation.ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process user tags for post: {}", post.getId(), e);
            throw new ServiceException("Failed to process user tags: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void addUserTag(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to add tag to post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot add tags to posts with status: " + post.getStatus().getDisplayName());
            }

            // Check if tag already exists and is active
            Optional<UserTag> existingTag = userTagRepository.findByPostAndTaggedUserAndIsActiveTrue(post, user);

            if (existingTag.isEmpty()) {
                UserTag userTag = UserTag.builder()
                        .post(post)
                        .taggedUser(user)
                        .taggedBy(post.getUser())
                        .isActive(true)
                        .taggedAt(new Date())
                        .build();

                userTagRepository.save(userTag);
                log.info("Added user tag: {} to post ID: {} (status: {})",
                        user.getActualUsername(), post.getId(), post.getStatus().getDisplayName());
            } else {
                log.debug("User tag already exists for user: {} on post: {}", user.getActualUsername(), post.getId());
            }
        } catch (jakarta.validation.ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to add user tag for user: {} to post: {}", user.getActualUsername(), post.getId(), e);
            throw new ServiceException("Failed to add user tag: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void removeUserTag(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to remove tag from post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot remove tags from posts with status: " + post.getStatus().getDisplayName());
            }

            Optional<UserTag> userTag = userTagRepository.findByPostAndTaggedUserAndIsActiveTrue(post, user);

            if (userTag.isPresent()) {
                userTag.get().deactivate(post.getUser(), "Tag removed");
                userTagRepository.save(userTag.get());
                log.info("Removed user tag: {} from post ID: {} (status: {})",
                        user.getActualUsername(), post.getId(), post.getStatus().getDisplayName());
            } else {
                log.warn("Attempted to remove non-existent user tag: {} from post: {}",
                        user.getActualUsername(), post.getId());
            }
        } catch (jakarta.validation.ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove user tag for user: {} from post: {}", user.getActualUsername(), post.getId(), e);
            throw new ServiceException("Failed to remove user tag: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updatePostTags(Post post, String newContent) {
        try {
            PostUtility.validatePost(post);
            if (newContent == null) {
                throw new jakarta.validation.ValidationException("New content cannot be null");
            }

            // Check if post allows tag updates based on status
            if (!PostUtility.postAllowsUpdates(post)) {
                log.warn("Attempted to update tags for post {} with status: {}",
                        post.getId(), post.getStatus().getDisplayName());
                throw new ServiceException("Cannot update tags for posts with status: " + post.getStatus().getDisplayName());
            }

            log.info("Updating post tags for post ID: {} (status: {})", post.getId(), post.getStatus().getDisplayName());

            // Get current active tags
            List<UserTag> currentTags = userTagRepository.findByPostAndIsActiveTrue(post);
            if (currentTags == null) {
                currentTags = Collections.emptyList();
            }

            // Extract new usernames from updated content
            List<String> newUsernames = PostUtility.extractUserTags(newContent);

            if (newUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                log.warn("Updated content has {} user tags, which exceeds the maximum of {}. Processing first {} tags.",
                        newUsernames.size(), Constant.MAX_TAGS_PER_POST, Constant.MAX_TAGS_PER_POST);
                newUsernames = newUsernames.subList(0, Constant.MAX_TAGS_PER_POST);
            }

            List<User> newTaggedUsers = userRepository.findByUsernameInAndIsActiveTrue(newUsernames);
            if (newTaggedUsers == null) {
                newTaggedUsers = Collections.emptyList();
            }

            // Deactivate current tags
            currentTags.forEach(tag -> tag.deactivate(post.getUser(), "Post content updated"));
            if (!currentTags.isEmpty()) {
                userTagRepository.saveAll(currentTags);
            }

            // Create new tags with location awareness using pincode prefix logic
            List<UserTag> newTags = new ArrayList<>();
            for (User user : newTaggedUsers) {
                String content = newContent;
                String username = "@" + user.getActualUsername();
                int startPos = content.indexOf(username);

                if (startPos != -1) {
                    UserTag userTag = UserTag.builder()
                            .post(post)
                            .taggedUser(user)
                            .taggedBy(post.getUser())
                            .isActive(true)
                            .taggedAt(new Date())
                            .build();

                    newTags.add(userTag);
                }
            }

            // Save new tags
            if (!newTags.isEmpty()) {
                userTagRepository.saveAll(newTags);
            }

            log.info("Updated post tags - removed {} old tags, added {} new tags for post ID: {} (status: {})",
                    currentTags.size(), newTags.size(), post.getId(), post.getStatus().getDisplayName());
        } catch (jakarta.validation.ValidationException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update post tags for post: {}", post.getId(), e);
            throw new ServiceException("Failed to update post tags: " + e.getMessage(), e);
        }
    }

    // ===== Geographic-Aware Query Methods Using Pincode Prefix Logic =====

    // CHANGED: This method now returns a response containing DTOs instead of raw entities.
    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getPostsVisibleToUser(User user, Boolean isResolved, Long beforeId, Integer limit) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getPostsVisibleToUser", beforeId, limit);

            List<Post> posts;
            if (setup.hasCursor()) {
                if (isResolved != null) {
                    PostStatus status = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                    posts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user.getId(), status, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    posts = userTagRepository.findPostsWhereUserIsTaggedWithCursor(user.getId(), setup.getSanitizedCursor(), setup.toPageable());
                }
            } else {
                if (isResolved != null) {
                    PostStatus status = isResolved ? PostStatus.RESOLVED : PostStatus.ACTIVE;
                    posts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user.getId(), status, setup.toPageable());
                } else {
                    posts = userTagRepository.findPostsWhereUserIsTaggedOrderByIdDesc(user.getId(), setup.toPageable());
                }
            }
            posts = (posts == null) ? Collections.emptyList() : posts;

            List<Post> filteredPosts = posts.stream()
                    .filter(post -> post != null && PostUtility.isPostStatusVisible(post) && PostUtility.isPostGeographicallyRelevantToUser(post, user))
                    .collect(Collectors.toList());

            List<PostResponse> postDtos = filteredPosts.stream()
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> entityResponse = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            return PaginatedResponse.of(postDtos, entityResponse.isHasMore(), entityResponse.getNextCursor(), setup.getValidatedLimit());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get posts visible to user: " + e.getMessage(), e);
        }
    }

    // CORRECTED: Method now returns a PaginatedResponse of DTOs
    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getActivePostsVisibleToUser(User user, Long beforeId, Integer limit, String sortDirection) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getActivePostsVisibleToUser", beforeId, limit);

            List<Post> activePosts;
            boolean isAsc = "asc".equalsIgnoreCase(sortDirection);
            if (setup.hasCursor()) {
                if (isAsc) {
                    activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursorAsc(user.getId(), PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user.getId(), PostStatus.ACTIVE, setup.getSanitizedCursor(), setup.toPageable());
                }
            } else {
                if (isAsc) {
                    activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdAsc(user.getId(), PostStatus.ACTIVE, setup.toPageable());
                } else {
                    activePosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user.getId(), PostStatus.ACTIVE, setup.toPageable());
                }
            }
            activePosts = (activePosts == null) ? Collections.emptyList() : activePosts;

            List<Post> filteredPosts = activePosts.stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.ACTIVE && PostUtility.isPostStatusVisible(post))
                    .collect(Collectors.toList());

            List<PostResponse> postDtos = filteredPosts.stream()
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> entityResponse = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            return PaginatedResponse.of(postDtos, entityResponse.isHasMore(), entityResponse.getNextCursor(), setup.getValidatedLimit());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get active posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get active posts visible to user: " + e.getMessage(), e);
        }
    }

    // CORRECTED: Method now returns a PaginatedResponse of DTOs
    @Transactional(readOnly = true)
    public PaginatedResponse<PostResponse> getResolvedPostsVisibleToUser(User user, Long beforeId, Integer limit, String sortDirection) {
        try {
            PostUtility.validateUser(user);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getResolvedPostsVisibleToUser", beforeId, limit);

            List<Post> resolvedPosts;
            boolean isAsc = "asc".equalsIgnoreCase(sortDirection);
            if (setup.hasCursor()) {
                if (isAsc) {
                    resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursorAsc(user.getId(), PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable());
                } else {
                    resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusWithCursor(user.getId(), PostStatus.RESOLVED, setup.getSanitizedCursor(), setup.toPageable());
                }
            } else {
                if (isAsc) {
                    resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdAsc(user.getId(), PostStatus.RESOLVED, setup.toPageable());
                } else {
                    resolvedPosts = userTagRepository.findPostsWhereUserIsTaggedByStatusOrderByIdDesc(user.getId(), PostStatus.RESOLVED, setup.toPageable());
                }
            }
            resolvedPosts = (resolvedPosts == null) ? Collections.emptyList() : resolvedPosts;

            List<Post> filteredPosts = resolvedPosts.stream()
                    .filter(post -> post != null && post.getStatus() == PostStatus.RESOLVED && PostUtility.isPostStatusVisible(post))
                    .collect(Collectors.toList());

            List<PostResponse> postDtos = filteredPosts.stream()
                    .map(post -> convertToPostResponse(post, user))
                    .collect(Collectors.toList());

            PaginatedResponse<Post> entityResponse = PaginationUtils.createPostResponse(filteredPosts, setup.getValidatedLimit());

            return PaginatedResponse.of(postDtos, entityResponse.isHasMore(), entityResponse.getNextCursor(), setup.getValidatedLimit());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolved posts visible to user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get resolved posts visible to user: " + e.getMessage(), e);
        }
    }

    public PaginatedResponse<User> getTaggedUsersInPost(Post post, Long beforeId, Integer limit) {
        try {
            PostUtility.validatePost(post);
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("getTaggedUsersInPost", beforeId, limit);

            List<UserTag> activeTags;
            if (setup.hasCursor()) {
                activeTags = userTagRepository.findByPostAndIsActiveTrueAndIdLessThanOrderByIdDesc(post, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                activeTags = userTagRepository.findByPostAndIsActiveTrueOrderByIdDesc(post, setup.toPageable());
            }

            if (activeTags == null) {
                activeTags = Collections.emptyList();
            }

            List<User> taggedUsers = activeTags.stream()
                    .filter(Objects::nonNull)
                    .map(UserTag::getTaggedUser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(taggedUsers, setup.getValidatedLimit());

            log.debug("Found {} tagged users in post ID: {} (status: {})",
                    taggedUsers.size(), post.getId(), post.getStatus().getDisplayName());

            PaginationUtils.logPaginationResults("getTaggedUsersInPost",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (jakarta.validation.ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagged users in post: {}", post.getId(), e);
            throw new ServiceException("Failed to get tagged users in post: " + e.getMessage(), e);
        }
    }

    public boolean isUserTaggedInPost(Post post, User user) {
        try {
            PostUtility.validatePost(post);
            PostUtility.validateUser(user);

            Boolean isTagged = userTagRepository.existsByPostAndTaggedUserAndIsActiveTrue(post, user);
            boolean tagged = isTagged != null && isTagged;

            log.debug("User {} {} tagged in post ID: {} (status: {})",
                    user.getActualUsername(), tagged ? "IS" : "IS NOT",
                    post.getId(), post.getStatus().getDisplayName());

            return tagged;
        } catch (jakarta.validation.ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check if user {} is tagged in post: {}", user.getActualUsername(), post.getId(), e);
            return false; // Return false on error to be safe
        }
    }

    public Map<String, Long> getTaggingStatistics(User user) {
        try {
            PostUtility.validateUser(user);

            Long totalTagged = userTagRepository.countByTaggedUser(user.getId());
            Long activeTagged = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.ACTIVE);
            Long resolvedTagged = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.RESOLVED);

            Map<String, Long> result = new HashMap<>();
            result.put("totalTaggedPosts", totalTagged != null ? totalTagged : 0L);
            result.put("activeTaggedPosts", activeTagged != null ? activeTagged : 0L);
            result.put("resolvedTaggedPosts", resolvedTagged != null ? resolvedTagged : 0L);

            double resolutionRate = (totalTagged == null || totalTagged == 0) ? 0.0 :
                    (double) (resolvedTagged != null ? resolvedTagged : 0L) / totalTagged * 100;
            result.put("resolutionRate", Math.round(resolutionRate));

            return result;
        } catch (jakarta.validation.ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tagging statistics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get tagging statistics: " + e.getMessage(), e);
        }
    }

    public PaginatedResponse<UserTagSuggestionDto> getUserTagSuggestions(String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTaggingPagination("getUserTagSuggestions", beforeId, limit);

            if (query == null || query.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;

            if (cleanQuery.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String sanitizedQuery = PostUtility.sanitizeSqlLike(cleanQuery.trim());

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.searchUsersForTaggingWithCursor(sanitizedQuery, setup.getSanitizedCursor(), setup.toPageable());
            } else {
                users = userRepository.searchUsersForTagging(sanitizedQuery, setup.toPageable());
            }

            if (users == null) {
                users = Collections.emptyList();
            }

            List<UserTagSuggestionDto> userDtos = users.stream()
                    .filter(Objects::nonNull)
                    .map(user -> UserTagSuggestionDto.builder()
                            .id(user.getId())
                            .username(user.getActualUsername())
                            .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getActualUsername())
                            .pincode(user.getPincode())
                            .profileImage(user.getProfileImage())
                            .isActive(user.getIsActive())
                            .role(user.getRole() != null ? user.getRole().getName() : null)
                            .bio(PostUtility.truncateText(user.getBio(), 100))
                            .taggableName("@" + user.getActualUsername())
                            .hasLocation(user.hasLocation())
                            .totalTaggedPosts(getUserTagCount(user))
                            .resolutionRate(calculateUserResolutionRate(user))
                            .build())
                    .collect(Collectors.toList());

            PaginatedResponse<UserTagSuggestionDto> response = PaginationUtils.createIdBasedResponse(
                    userDtos, setup.getValidatedLimit(), UserTagSuggestionDto::getId);

            PaginationUtils.logPaginationResults("getUserTagSuggestions",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get user tag suggestions for query: {}", query, e);
            throw new ServiceException("Failed to get user tag suggestions: " + e.getMessage(), e);
        }
    }

    public UserTagValidationResult validateUserTags(String content) {
        try {
            if (content == null) {
                content = "";
            }

            List<String> extractedUsernames = PostUtility.extractUserTags(content);

            if (extractedUsernames.isEmpty()) {
                return UserTagValidationResult.builder()
                        .isValid(true)
                        .validUsernames(Collections.emptyList())
                        .invalidUsernames(Collections.emptyList())
                        .warnings(Collections.emptyList())
                        .totalTaggedUsers(0)
                        .message("No user tags found")
                        .exceedsMaxTags(false)
                        .maxTagsAllowed(Constant.MAX_TAGS_PER_POST)
                        .build();
            }

            List<User> validUsers = userRepository.findByUsernameInAndIsActiveTrue(extractedUsernames);
            if (validUsers == null) {
                validUsers = Collections.emptyList();
            }

            List<String> validUsernames = validUsers.stream()
                    .filter(Objects::nonNull)
                    .map(User::getActualUsername)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> invalidUsernames = extractedUsernames.stream()
                    .filter(username -> !validUsernames.contains(username))
                    .collect(Collectors.toList());

            List<User> allUsers = userRepository.findByUsernameIn(extractedUsernames);
            if (allUsers == null) {
                allUsers = Collections.emptyList();
            }

            List<String> allValidUsernames = allUsers.stream()
                    .filter(Objects::nonNull)
                    .map(User::getActualUsername)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> inactiveUsernames = allValidUsernames.stream()
                    .filter(username -> !validUsernames.contains(username))
                    .collect(Collectors.toList());

            List<String> duplicateUsernames = extractedUsernames.stream()
                    .filter(username -> Collections.frequency(extractedUsernames, username) > 1)
                    .distinct()
                    .collect(Collectors.toList());

            List<String> warnings = new ArrayList<>();
            if (extractedUsernames.size() > Constant.MAX_TAGS_PER_POST) {
                warnings.add("Tagging more than " + Constant.MAX_TAGS_PER_POST + " users may result in notification spam");
            }
            if (!inactiveUsernames.isEmpty()) {
                warnings.add("Some tagged users are inactive: " + String.join(", ", inactiveUsernames));
            }
            if (!duplicateUsernames.isEmpty()) {
                warnings.add("Duplicate tags found: " + String.join(", ", duplicateUsernames));
            }

            String message = "";
            if (invalidUsernames.isEmpty() && validUsernames.size() > 0) {
                message = "All " + validUsernames.size() + " user tags are valid";
            } else if (invalidUsernames.size() > 0 && validUsernames.size() > 0) {
                message = validUsernames.size() + " valid tags, " + invalidUsernames.size() + " invalid tags";
            } else if (invalidUsernames.size() > 0) {
                message = "All " + invalidUsernames.size() + " user tags are invalid";
            }

            return UserTagValidationResult.builder()
                    .isValid(invalidUsernames.isEmpty())
                    .validUsernames(validUsernames)
                    .invalidUsernames(invalidUsernames)
                    .warnings(warnings)
                    .totalTaggedUsers(validUsernames.size())
                    .message(message)
                    .inactiveUsernames(inactiveUsernames)
                    .duplicateUsernames(duplicateUsernames)
                    .maxTagsAllowed(Constant.MAX_TAGS_PER_POST)
                    .exceedsMaxTags(extractedUsernames.size() > Constant.MAX_TAGS_PER_POST)
                    .build();
        } catch (Exception e) {
            log.error("Failed to validate user tags", e);
            throw new ServiceException("Failed to validate user tags: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getComprehensiveTaggingAnalytics(User user) {
        try {
            PostUtility.validateUser(user);
            Map<String, Object> analytics = new HashMap<>();
            Map<String, Long> basicStats = getTaggingStatistics(user);
            analytics.putAll(basicStats);

            PaginatedResponse<PostResponse> allTaggedPosts = getPostsVisibleToUser(user, null, null, 1000); // Now gets PostResponse
            Map<String, Object> statusBreakdown = new HashMap<>();

            for (PostStatus status : PostStatus.values()) {
                long count = allTaggedPosts.getData().stream()
                        .filter(post -> post != null && post.getStatus() == status)
                        .count();
                Map<String, Object> statusInfo = new HashMap<>();
                statusInfo.put("count", count);
                statusInfo.put("displayName", status.getDisplayName());
                statusInfo.put("allowsUpdates", status.allowsUpdates());
                statusInfo.put("isVisible", status.isVisible());
                statusBreakdown.put(status.name(), statusInfo);
            }
            analytics.put("statusBreakdown", statusBreakdown);

            Date thirtyDaysAgo = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
            Long recentTags = userTagRepository.countByTaggedUserAndTaggedAtAfter(user.getId(), thirtyDaysAgo);
            analytics.put("recentTags30Days", recentTags != null ? recentTags : 0L);

            PaginatedResponse<PostResponse> resolvedPosts = getResolvedPostsVisibleToUser(user, null, 1000, "desc"); // Now gets PostResponse
            OptionalDouble avgResolutionTime = resolvedPosts.getData().stream()
                    .filter(post -> post != null && post.getResolvedAt() != null && post.getCreatedAt() != null)
                    .mapToLong(post -> post.getResolvedAt().getTime() - post.getCreatedAt().getTime())
                    .average();

            if (avgResolutionTime.isPresent()) {
                long avgHours = (long) avgResolutionTime.getAsDouble() / (1000 * 60 * 60);
                analytics.put("averageResolutionTimeHours", avgHours);
            }

            return analytics;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get comprehensive tagging analytics for user: {}", user.getActualUsername(), e);
            throw new ServiceException("Failed to get comprehensive tagging analytics: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getComprehensiveAnalyticsByUsername(String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new ValidationException("Username cannot be empty");
            }
            String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
            User user = userRepository.findByUsernameAndIsActiveTrue(cleanUsername)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + cleanUsername));
            return getComprehensiveTaggingAnalytics(user);
        } catch (UserNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get comprehensive analytics by username: {}", username, e);
            throw new ServiceException("Failed to get comprehensive analytics by username: " + e.getMessage(), e);
        }
    }

    private Long getUserTagCount(User user) {
        try {
            if (user == null) {
                return 0L;
            }
            Long count = userTagRepository.countByTaggedUser(user.getId());
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to get tag count for user: {}", user.getActualUsername(), e);
            return 0L;
        }
    }

    private double calculateUserResolutionRate(User user) {
        try {
            Long totalTagged = userTagRepository.countByTaggedUser(user.getId());
            if (totalTagged == null || totalTagged == 0) {
                return 0.0;
            }

            Long resolved = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.RESOLVED);
            if (resolved == null) {
                resolved = 0L;
            }

            return PostUtility.calculateUserResolutionRate(user, totalTagged, resolved);
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getActualUsername(), e);
            return 0.0;
        }
    }

    // CHANGED: This method now returns a Map containing DTOs.
    @Transactional(readOnly = true)
    public Map<String, Object> getActivePostsByUsername(String username, Long beforeId, Integer limit, String sortDirection) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new ValidationException("Username cannot be empty");
            }

            String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
            User user = userRepository.findByUsernameAndIsActiveTrue(cleanUsername)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + cleanUsername));

            PaginatedResponse<PostResponse> posts = getActivePostsVisibleToUser(user, beforeId, limit, sortDirection);
            Long totalActiveCount = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.ACTIVE);

            Map<String, Object> result = new HashMap<>();
            result.put("username", cleanUsername);
            result.put("userId", user.getId());
            result.put("displayName", user.getDisplayName());
            result.put("posts", posts); // This now contains PostResponse DTOs
            result.put("count", totalActiveCount != null ? totalActiveCount : 0L);

            log.info("Retrieved {} active tagged posts for username: {} (total: {})",
                    posts.getData().size(), cleanUsername, totalActiveCount);

            return result;
        } catch (UserNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get active posts by username: {}", username, e);
            throw new ServiceException("Failed to get active posts by username: " + e.getMessage(), e);
        }
    }

    // CORRECTED: This method now correctly uses the DTO-returning service method
    @Transactional(readOnly = true)
    public Map<String, Object> getResolvedPostsByUsername(String username, Long beforeId, Integer limit, String sortDirection) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new ValidationException("Username cannot be empty");
            }

            String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
            User user = userRepository.findByUsernameAndIsActiveTrue(cleanUsername)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + cleanUsername));

            PaginatedResponse<PostResponse> posts = getResolvedPostsVisibleToUser(user, beforeId, limit, sortDirection);
            Long totalResolvedCount = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.RESOLVED);

            Map<String, Object> result = new HashMap<>();
            result.put("username", cleanUsername);
            result.put("userId", user.getId());
            result.put("displayName", user.getDisplayName());
            result.put("posts", posts); // This now contains PostResponse DTOs
            result.put("count", totalResolvedCount != null ? totalResolvedCount : 0L);

            log.info("Retrieved {} resolved tagged posts for username: {} (total: {})",
                    posts.getData().size(), cleanUsername, totalResolvedCount);

            return result;
        } catch (UserNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resolved posts by username: {}", username, e);
            throw new ServiceException("Failed to get resolved posts by username: " + e.getMessage(), e);
        }
    }

    private PostResponse convertToPostResponse(Post post, User currentUser) {
        if (post == null) {
            return null;
        }

        try {
            Long userId = null;
            String username = null;
            String userDisplayName = null;
            String userPincode = null;
            String userProfileImage = null;

            if (post.getUser() != null) {
                userId = post.getUser().getId();
                username = post.getUser().getActualUsername();
                userDisplayName = post.getUser().getDisplayName();
                userPincode = post.getUser().getPincode();
                userProfileImage = post.getUser().getProfileImage();
            }

            boolean isLiked = false;
            if (currentUser != null) {
                isLiked = postLikeRepo.findByPostAndUserId(post, currentUser.getId()).isPresent();
            }

            PostResponse.PostResponseBuilder builder = PostResponse.builder()
                    .id(post.getId())
                    .content(post.getContent())
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .imageName(post.getImageName())
                    .hasImage(post.hasImage())
                    .mediaType(post.hasImage() ? getSimpleMediaType(post.getImageName()) : null)
                    .isResolved(post.isResolved())
                    .resolvedAt(post.getResolvedAt())
                    .userId(userId)
                    .username(username)
                    .userDisplayName(userDisplayName != null ? userDisplayName : username)
                    .userProfileImage(userProfileImage)
                    .userPincode(userPincode)
                    .broadcastScope(post.getBroadcastScope())
                    .broadcastScopeDescription(post.getBroadcastScopeDescription())
                    .isBroadcastPost(post.isBroadcastPost())
                    .targetCountry(post.getTargetCountry())
                    .targetStates(post.getTargetStatesList())
                    .targetDistricts(post.getTargetDistrictsList())
                    .targetPincodes(post.getTargetPincodesList())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(post.getViewCount())
                    .isLikedByCurrentUser(isLiked)
                    .isViewedByCurrentUser(false)
                    .isVisibleToCurrentUser(currentUser != null ? PostUtility.isPostVisibleToUser(post, currentUser) : false)
                    .statusDisplayName(post.getStatus() != null ? post.getStatus().getDisplayName() : null)
                    .canBeResolved(post.getStatus() == PostStatus.ACTIVE)
                    .allowsUpdates(PostUtility.postAllowsUpdates(post))
                    .isEligibleForDisplay(PostUtility.isPostEligibleForDisplay(post))
                    .timeAgo(calculateTimeAgo(post.getCreatedAt()));

            try {
                List<UserTag> activeTags = userTagRepository.findByPostAndIsActiveTrue(post);
                List<String> taggedUsernames = activeTags.stream()
                        .map(tag -> tag.getTaggedUser().getActualUsername())
                        .collect(Collectors.toList());

                List<PostResponse.TaggedUserInfo> taggedUserInfos = activeTags.stream()
                        .map(this::convertToTaggedUserInfo)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                builder.taggedUsernames(taggedUsernames)
                        .taggedUsers(taggedUserInfos)
                        .taggedUserCount(activeTags.size());

            } catch (Exception e) {
                log.warn("Failed to get tagged users for post: {}", post.getId(), e);
                builder.taggedUsernames(Collections.emptyList())
                        .taggedUsers(Collections.emptyList())
                        .taggedUserCount(0);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to convert post to response: {} for user: {}",
                    post.getId(), currentUser != null ? currentUser.getActualUsername() : "null", e);
            return null;
        }
    }

    private PostResponse.TaggedUserInfo convertToTaggedUserInfo(UserTag userTag) {
        if (userTag == null || userTag.getTaggedUser() == null) {
            return null;
        }

        try {
            User taggedUser = userTag.getTaggedUser();
            User taggedBy = userTag.getTaggedBy();

            return PostResponse.TaggedUserInfo.builder()
                    .tagId(userTag.getId())
                    .userId(taggedUser.getId())
                    .username(taggedUser.getActualUsername())
                    .displayName(taggedUser.getDisplayName())
                    .profileImage(taggedUser.getProfileImage())
                    .pincode(taggedUser.getPincode())
                    .isActive(userTag.getIsActive())
                    .taggedAt(userTag.getTaggedAt())
                    .deactivatedAt(userTag.getDeactivatedAt())
                    .deactivationReason(userTag.getDeactivationReason())
                    .taggedByUserId(taggedBy != null ? taggedBy.getId() : null)
                    .taggedByUsername(taggedBy != null ? taggedBy.getActualUsername() : null)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to convert tagged user info for tag: {}", userTag.getId(), e);
            return null;
        }
    }
    private String getSimpleMediaType(String fileName) {
        if (fileName == null) return "unknown";
        String extension = fileName.toLowerCase();
        if (extension.contains(".jpg") || extension.contains(".jpeg") ||
                extension.contains(".png") || extension.contains(".webp")) {
            return "image";
        }
        if (extension.contains(".mp4") || extension.contains(".mov")) {
            return "video";
        }
        return "unknown";
    }
}
