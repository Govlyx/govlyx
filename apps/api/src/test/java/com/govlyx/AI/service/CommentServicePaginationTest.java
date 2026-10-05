package com.Govlyx.AI.service;

import com.Govlyx.AI.dto.CommentDto;
import com.Govlyx.AI.dto.PaginatedResponse;
import com.Govlyx.AI.model.Comment;
import com.Govlyx.AI.model.Post;
import com.Govlyx.AI.model.SocialPost;
import com.Govlyx.AI.enums.PostStatus;
import com.Govlyx.AI.model.User;
import com.Govlyx.AI.repository.CommentInteractionRepository;
import com.Govlyx.AI.repository.CommentRepo;
import com.Govlyx.AI.repository.PostRepo;
import com.Govlyx.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentServicePaginationTest {

    @Mock
    private CommentRepo commentRepository;

    @Mock
    private PostRepo postRepository;

    @Mock
    private SocialPostRepo socialPostRepository;

    @Mock
    private ContentValidationService contentValidationService;

    @Mock
    private CommentInteractionRepository commentInteractionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PostService postService;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private CommentService commentService;

    private Post mockPost;
    private SocialPost mockSocialPost;
    private User mockUser;
    private Comment comment1;
    private Comment comment2;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setIsActive(true);

        PostStatus status = PostStatus.ACTIVE;

        mockPost = new Post();
        mockPost.setId(100L);
        mockPost.setStatus(status);
        mockPost.setContent("Test post content");

        mockSocialPost = new SocialPost();
        mockSocialPost.setId(200L);
        mockSocialPost.setStatus(PostStatus.ACTIVE);
        mockSocialPost.setContent("Test social post content");
        mockSocialPost.setUser(mockUser);
        mockSocialPost.setIsFlagged(false);

        comment1 = new Comment();
        comment1.setId(10L);
        comment1.setText("Comment 1");
        comment1.setLikeCount(5);
        comment1.setCreatedAt(new Date());
        comment1.setUser(mockUser);

        comment2 = new Comment();
        comment2.setId(9L);
        comment2.setText("Comment 2");
        comment2.setLikeCount(2);
        comment2.setCreatedAt(new Date(System.currentTimeMillis() - 10000));
        comment2.setUser(mockUser);
    }

    @Test
    void getCommentsByPost_sortNew_usesCursorPagination() {
        List<Comment> comments = Arrays.asList(comment1, comment2);
        when(commentRepository.findByPostAndIdLessThanOrderByCreatedAtDesc(eq(mockPost), eq(11L), any(Pageable.class)))
                .thenReturn(comments);

        PaginatedResponse<CommentDto> response = commentService.getCommentsByPost(mockPost, 11L, 10, "NEW", mockUser);

        assertNotNull(response);
        assertEquals(2, response.getData().size());
        verify(commentRepository).findByPostAndIdLessThanOrderByCreatedAtDesc(eq(mockPost), eq(11L), any(Pageable.class));
        verify(commentRepository, never()).findTopRatedByPost(any(), any());
    }

    @Test
    void getCommentsByPost_sortTop_usesOffsetPagination() {
        List<Comment> comments = Arrays.asList(comment1, comment2);
        when(commentRepository.findTopRatedByPost(eq(mockPost), any(Pageable.class)))
                .thenReturn(comments);

        // offset passed via beforeId
        PaginatedResponse<CommentDto> response = commentService.getCommentsByPost(mockPost, 10L, 10, "TOP", mockUser);

        assertNotNull(response);
        assertEquals(2, response.getData().size());
        
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findTopRatedByPost(eq(mockPost), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(1, capturedPageable.getPageNumber()); // offset 10 / limit 10 = page 1
        assertEquals(11, capturedPageable.getPageSize()); // limit 10 + 1

        verify(commentRepository, never()).findByPostAndIdLessThanOrderByCreatedAtDesc(any(), any(), any());
        verify(commentRepository, never()).findByPostOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getCommentsBySocialPost_sortTop_usesOffsetPagination() {
        List<Comment> comments = Arrays.asList(comment1, comment2);
        when(commentRepository.findTopRatedBySocialPostId(eq(mockSocialPost.getId()), any(Pageable.class)))
                .thenReturn(comments);

        PaginatedResponse<CommentDto> response = commentService.getCommentsBySocialPost(mockSocialPost, 0L, 5, "TOP", mockUser);

        assertNotNull(response);
        assertEquals(2, response.getData().size());
        
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findTopRatedBySocialPostId(eq(mockSocialPost.getId()), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber()); // offset 0 / limit 5 = page 0
        assertEquals(6, capturedPageable.getPageSize()); // limit 5 + 1
    }
}
