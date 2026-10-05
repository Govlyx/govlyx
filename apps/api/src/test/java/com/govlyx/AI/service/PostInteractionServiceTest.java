package com.Govlyx.AI.service;

import com.Govlyx.AI.enums.PostStatus;
import com.Govlyx.AI.model.Post;
import com.Govlyx.AI.model.PostView;
import com.Govlyx.AI.model.SocialPost;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.PostRepo;
import com.Govlyx.AI.repository.PostViewRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostInteractionServiceTest {

    @Mock
    private PostRepo postRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private PostViewRepo postViewRepository;

    @Mock
    private InterestProfileService interestProfileService;

    @Mock
    private PostInteractionService selfProxy;

    @Mock
    private com.Govlyx.AI.repository.PostLikeRepo postLikeRepository;

    @Mock
    private com.Govlyx.AI.repository.CommentRepo commentRepo;

    @Mock
    private PostService postService;

    @Mock
    private SocialPostService socialPostService;

    @InjectMocks
    private PostInteractionService postInteractionService;

    private User user;
    private Post post;
    private SocialPost socialPost;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("test_user");

        post = new Post();
        post.setId(100L);
        post.setContent("This is a test post content");
        post.setStatus(PostStatus.ACTIVE);

        socialPost = new SocialPost();
        socialPost.setId(200L);
        socialPost.setContent("This is a test social post content");
        socialPost.setStatus(PostStatus.ACTIVE);
        socialPost.setUser(user);

        ReflectionTestUtils.setField(postInteractionService, "self", selfProxy);
        ReflectionTestUtils.setField(postInteractionService, "socialPostService", socialPostService);
        ReflectionTestUtils.setField(postInteractionService, "postService", postService);
    }

    @Test
    void recordPostView_ShouldDispatchToAsyncAndNotSavePostEntity_PreventingLostUpdate() {
        // Arrange
        when(postViewRepository.findByPostAndUserIdAndViewedAtAfter(any(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(postViewRepository.save(any(PostView.class))).thenReturn(new PostView());

        // Act
        PostView result = postInteractionService.recordPostView(post, user);

        // Assert
        assertNotNull(result);
        
        // Ensure that the view row is saved
        verify(postViewRepository, times(1)).save(any(PostView.class));
        
        // Ensure that the increment is dispatched to the @Async proxy method
        verify(selfProxy, times(1)).executeIncrementPostViewAsync(post.getId());
        
        // CRITICAL CHECK: Ensure the main post entity is NEVER saved via JPA save(), preventing "Lost Update"
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void recordSocialPostView_ShouldDispatchToAsyncAndNotSaveSocialPostEntity_PreventingLostUpdate() {
        // Arrange
        when(postViewRepository.findBySocialPostAndUserIdAndViewedAtAfter(any(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(postViewRepository.save(any(PostView.class))).thenReturn(new PostView());

        // Act
        PostView result = postInteractionService.recordSocialPostView(socialPost, user);

        // Assert
        assertNotNull(result);
        
        // Ensure that the view row is saved
        verify(postViewRepository, times(1)).save(any(PostView.class));
        
        // Ensure that the increment is dispatched to the @Async proxy method
        verify(selfProxy, times(1)).executeIncrementSocialPostViewAsync(socialPost.getId());
        
        // CRITICAL CHECK: Ensure the main social post entity is NEVER saved via JPA save(), preventing "Lost Update"
        verify(socialPostRepository, never()).save(any(SocialPost.class));
    }

    @Test
    void getLikedSocialPostsForUser_ShouldReturnCorrectDtoWithReactionType() {
        com.Govlyx.AI.model.PostLike like = new com.Govlyx.AI.model.PostLike();
        like.setId(10L);
        like.setUser(user);
        like.setSocialPost(socialPost);
        like.setReactionType(com.Govlyx.AI.model.PostLike.ReactionType.LIKE);
        like.setCreatedAt(new java.util.Date());

        org.springframework.data.domain.Page<com.Govlyx.AI.model.PostLike> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(like));

        when(postLikeRepository.findBySocialPostNotNullAndUserIdOrderByCreatedAtDesc(eq(user.getId()), any()))
                .thenReturn(page);
        when(socialPostService.convertToDto(eq(socialPost), eq(user)))
                .thenReturn(com.Govlyx.AI.dto.SocialPostDto.builder().id(200L).build());

        org.springframework.data.domain.Page<com.Govlyx.AI.dto.PostInteractionDto> result =
                postInteractionService.getLikedSocialPostsForUser(user, 0, 10);

        assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals("LIKE", result.getContent().get(0).getInteractionType());
        org.junit.jupiter.api.Assertions.assertEquals("SOCIAL", result.getContent().get(0).getPostType());
    }

    @Test
    void getCommentedBroadcastPostsForUser_ShouldPassUserIdToRepo() {
        com.Govlyx.AI.model.Comment comment = new com.Govlyx.AI.model.Comment();
        comment.setId(50L);
        comment.setUser(user);
        comment.setPost(post);
        comment.setText("Great initiative");
        comment.setCreatedAt(new java.util.Date());

        org.springframework.data.domain.Page<com.Govlyx.AI.model.Comment> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(comment));

        when(commentRepo.findByPostNotNullAndUserIdOrderByCreatedAtDesc(eq(user.getId()), any()))
                .thenReturn(page);
        when(postService.convertToPostResponse(eq(post), eq(user)))
                .thenReturn(com.Govlyx.AI.dto.PostResponse.builder().id(100L).build());

        org.springframework.data.domain.Page<com.Govlyx.AI.dto.PostInteractionDto> result =
                postInteractionService.getCommentedBroadcastPostsForUser(user, 0, 10);

        assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals("COMMENT", result.getContent().get(0).getInteractionType());
        org.junit.jupiter.api.Assertions.assertEquals("ISSUE", result.getContent().get(0).getPostType());
    }
}
