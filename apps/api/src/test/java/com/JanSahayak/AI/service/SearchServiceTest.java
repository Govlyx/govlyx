package com.JanSahayak.AI.service;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.PostResponse;
import com.JanSahayak.AI.dto.SearchDto;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Community;
import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.model.SocialPost;
import com.JanSahayak.AI.repository.CommunityRepo;
import com.JanSahayak.AI.repository.PostRepo;
import com.JanSahayak.AI.repository.SocialPostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest {

    @Mock
    private PostRepo postRepo;

    @Mock
    private SocialPostRepo socialPostRepo;

    @Mock
    private CommunityRepo communityRepo;

    @Mock
    private PostService postService;

    @InjectMocks
    private SearchService searchService;

    private Post mockPost;
    private SocialPost mockSocialPost;
    private Community mockCommunity;

    @BeforeEach
    void setUp() {
        mockPost = Post.builder()
                .id(101L)
                .content("Chess championship civic issue")
                .build();

        mockSocialPost = SocialPost.builder()
                .id(202L)
                .content("Amazing moves in today's game #chess")
                .hashtags("#chess")
                .hashtagCount(1)
                .build();

        mockCommunity = Community.builder()
                .id(303L)
                .name("Chess Masters Club")
                .slug("chess-masters")
                .description("A community for passionate chess players")
                .build();
    }

    @Test
    void testHashtagSearch_Unified_ReturnsPostsSocialPostsCommunitiesAndHashtags() {
        // Given request with #chess
        SearchDto.Request req = new SearchDto.Request();
        req.setQuery("#chess");
        req.setLimit(20);

        when(postRepo.searchPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(mockPost));
        when(postService.convertToPostResponse(eq(mockPost), isNull()))
                .thenReturn(PostResponse.builder().id(101L).content(mockPost.getContent()).build());

        when(socialPostRepo.searchByHashtagPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(mockSocialPost));

        when(communityRepo.searchPage(eq("chess"), any(Pageable.class)))
                .thenReturn(List.of(mockCommunity));

        List<Object[]> hashtagRows = Collections.singletonList(new Object[]{"chess", 5L});
        when(socialPostRepo.findTopHashtags(eq("chess"), anyInt()))
                .thenReturn(hashtagRows);

        // When
        SearchDto.Response response = searchService.search(req);

        // Then
        assertNotNull(response);
        assertEquals("#chess", response.getQuery());
        assertFalse(response.getData().isEmpty());

        // Verify all 4 types are present in grouped response
        assertTrue(response.getGrouped().containsKey("POST"), "Should contain POST results");
        assertTrue(response.getGrouped().containsKey("SOCIAL_POST"), "Should contain SOCIAL_POST results");
        assertTrue(response.getGrouped().containsKey("COMMUNITY"), "Should contain COMMUNITY results");
        assertTrue(response.getGrouped().containsKey("HASHTAG"), "Should contain HASHTAG results");

        // Verify counts
        assertEquals(1, response.getCountByType().get("POST"));
        assertEquals(1, response.getCountByType().get("SOCIAL_POST"));
        assertEquals(1, response.getCountByType().get("COMMUNITY"));
        assertEquals(1, response.getCountByType().get("HASHTAG"));

        // Verify underlying repository calls used clean tag "chess"
        verify(postRepo).searchPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class));
        verify(socialPostRepo).searchByHashtagPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class));
        verify(communityRepo).searchPage(eq("chess"), any(Pageable.class));
        verify(socialPostRepo).findTopHashtags(eq("chess"), anyInt());
    }

    @Test
    void testHashtagSearch_SearchByType_Community() {
        when(communityRepo.searchPage(eq("chess"), any(Pageable.class)))
                .thenReturn(List.of(mockCommunity));

        PaginatedResponse<SearchDto.Result> response =
                searchService.searchByType("#chess", "COMMUNITY", null, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals("COMMUNITY", response.getData().get(0).getResultType());
        assertEquals("Chess Masters Club", response.getData().get(0).getCommunityName());

        verify(communityRepo).searchPage(eq("chess"), any(Pageable.class));
    }

    @Test
    void testHashtagSearch_SearchByType_SocialPost() {
        when(socialPostRepo.searchByHashtagPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(mockSocialPost));

        PaginatedResponse<SearchDto.Result> response =
                searchService.searchByType("#chess", "SOCIAL_POST", null, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals("SOCIAL_POST", response.getData().get(0).getResultType());
        assertEquals(202L, response.getData().get(0).getId());

        verify(socialPostRepo).searchByHashtagPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void testHashtagSearch_SearchByType_Post() {
        when(postRepo.searchPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(mockPost));
        when(postService.convertToPostResponse(eq(mockPost), isNull()))
                .thenReturn(PostResponse.builder().id(101L).content(mockPost.getContent()).build());

        PaginatedResponse<SearchDto.Result> response =
                searchService.searchByType("#chess", "POST", null, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals("POST", response.getData().get(0).getResultType());
        assertEquals(101L, response.getData().get(0).getId());

        verify(postRepo).searchPage(eq("chess"), eq(PostStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void testHashtagSearch_SearchByType_Hashtag() {
        List<Object[]> hashtagRows = Collections.singletonList(new Object[]{"chess", 12L});
        when(socialPostRepo.findTopHashtags(eq("chess"), anyInt()))
                .thenReturn(hashtagRows);

        PaginatedResponse<SearchDto.Result> response =
                searchService.searchByType("#chess", "HASHTAG", null, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        assertEquals("HASHTAG", response.getData().get(0).getResultType());
        assertEquals("#chess", response.getData().get(0).getHashtag());
        assertEquals(12L, response.getData().get(0).getPostCount());

        verify(socialPostRepo).findTopHashtags(eq("chess"), anyInt());
    }

    @Test
    void testRegularSearch_NormalKeyword_StillWorks() {
        SearchDto.Request req = new SearchDto.Request();
        req.setQuery("water");
        req.setLimit(20);

        when(postRepo.searchPage(eq("water"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(socialPostRepo.searchPage(eq("water"), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(communityRepo.searchPage(eq("water"), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(socialPostRepo.findTopHashtags(eq("water"), anyInt()))
                .thenReturn(Collections.emptyList());

        SearchDto.Response response = searchService.search(req);

        assertNotNull(response);
        assertEquals("water", response.getQuery());

        // Regular search calls searchPage on socialPostRepo (not searchByHashtagPage)
        verify(socialPostRepo).searchPage(eq("water"), eq(PostStatus.ACTIVE), any(Pageable.class));
        verify(socialPostRepo, never()).searchByHashtagPage(anyString(), any(), any());
    }
}
