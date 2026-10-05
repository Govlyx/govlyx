package com.govlyx.AI.controller;

import com.govlyx.AI.model.Community;
import com.govlyx.AI.model.Post;
import com.govlyx.AI.model.SocialPost;
import com.govlyx.AI.repository.CommunityRepo;
import com.govlyx.AI.repository.PincodeLookupRepo;
import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.SocialPostRepo;
import com.govlyx.AI.service.SitemapGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/public/ssr")
@RequiredArgsConstructor
@Slf4j
public class SsrController {

    private final PostRepo postRepo;
    private final SocialPostRepo socialPostRepo;
    private final CommunityRepo communityRepo;
    private final PincodeLookupRepo pincodeLookupRepo;

    @Value("${app.base-url:https://govlyx.com}")
    private String baseUrl;

    @GetMapping(value = "/issue/{id}/**", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ssrIssue(@PathVariable Long id) {
        Optional<Post> postOpt = postRepo.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        Post post = postOpt.get();

        String title = truncate(post.getContent(), 60) + " — Civic Issue | Govlyx";
        String description = "Civic issue in " + post.getPostPincode() + ". "
                + post.getLikeCount() + " upvotes. Read and track resolution on Govlyx.";
        String url = baseUrl + "/issue/" + id + "/" + SitemapGeneratorService.toSlug(post.getContent());
        // Prefer actual post image; fall back to the PNG social card (NOT svg — social platforms don't render svg)
        String image = (post.getImageName() != null && !post.getImageName().isBlank())
                ? post.getImageName()
                : baseUrl + "/govlyx-og.png";

        String jsonLd = String.format("""
            {
              "@context": "https://schema.org",
              "@type": "SocialMediaPosting",
              "headline": "%s",
              "articleBody": "%s",
              "interactionStatistic": [
                { "@type": "InteractionCounter", "interactionType": "LikeAction", "userInteractionCount": %d },
                { "@type": "InteractionCounter", "interactionType": "CommentAction", "userInteractionCount": %d }
              ]
            }
            """, escapeJson(truncate(post.getContent(), 60)), escapeJson(post.getContent()), post.getLikeCount(), post.getCommentCount());

        return ResponseEntity.ok(generateHtml(title, description, url, image, "article", jsonLd, post.getContent()));
    }

    @GetMapping(value = "/q/{id}/**", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ssrQa(@PathVariable Long id) {
        Optional<SocialPost> postOpt = socialPostRepo.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        SocialPost post = postOpt.get();
        
        String title = truncate(post.getContent(), 70) + " — Neighbourhood Q&A | Govlyx";
        String description = "Question in " + post.getPincode() + ". "
                + post.getCommentCount() + " answers. Join the discussion on Govlyx.";
        String url = baseUrl + "/q/" + id + "/" + SitemapGeneratorService.toSlug(post.getContent());
        String image = baseUrl + "/govlyx-og.png";

        String jsonLd = String.format("""
            {
              "@context": "https://schema.org",
              "@type": "QAPage",
              "name": "%s",
              "mainEntity": {
                "@type": "Question",
                "name": "%s",
                "text": "%s",
                "answerCount": %d,
                "upvoteCount": %d
              }
            }
            """, escapeJson(truncate(post.getContent(), 70)), escapeJson(truncate(post.getContent(), 70)), escapeJson(post.getContent()), post.getCommentCount(), post.getLikeCount());

        return ResponseEntity.ok(generateHtml(title, description, url, image, "article", jsonLd, post.getContent()));
    }

    @GetMapping(value = "/communities/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ssrCommunity(@PathVariable String slug) {
        Optional<Community> communityOpt = communityRepo.findBySlug(slug);
        if (communityOpt.isEmpty()) return ResponseEntity.notFound().build();
        Community community = communityOpt.get();

        String communityName = community.getName() != null ? community.getName() : slug;
        String communityDesc = (community.getDescription() != null && !community.getDescription().isBlank())
                ? community.getDescription()
                : "Join the " + communityName + " community on Govlyx to discuss local issues and connect with neighbors.";

        String title = communityName + " Community | Govlyx";
        String url = baseUrl + "/communities/" + slug;
        String image = baseUrl + "/govlyx-og.png";

        String jsonLd = String.format("""
            {
              "@context": "https://schema.org",
              "@type": "Organization",
              "name": "%s",
              "description": "%s",
              "url": "%s"
            }
            """, escapeJson(communityName), escapeJson(communityDesc), url);

        return ResponseEntity.ok(generateHtml(title, communityDesc, url, image, "website", jsonLd, communityDesc));
    }

    private String generateHtml(String title, String desc, String url, String image,
                                 String ogType, String jsonLd, String bodySnippet) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s</title>
              <meta name="description" content="%s">
              <!-- Open Graph -->
              <meta property="og:type" content="%s">
              <meta property="og:site_name" content="Govlyx">
              <meta property="og:locale" content="en_IN">
              <meta property="og:title" content="%s">
              <meta property="og:description" content="%s">
              <meta property="og:image" content="%s">
              <meta property="og:image:width" content="1200">
              <meta property="og:image:height" content="630">
              <meta property="og:url" content="%s">
              <!-- Twitter Card -->
              <meta name="twitter:card" content="summary_large_image">
              <meta name="twitter:title" content="%s">
              <meta name="twitter:description" content="%s">
              <meta name="twitter:image" content="%s">
              <!-- Canonical -->
              <link rel="canonical" href="%s">
              <script type="application/ld+json">%s</script>
            </head>
            <body>
              <div id="root">
                  <h1>%s</h1>
                  <p>%s</p>
                  <p>View full post on <a href="%s">Govlyx</a></p>
              </div>
            </body>
            </html>
            """,
            title, desc,
            ogType, title, desc, image, url,
            title, desc, image,
            url, jsonLd,
            title, bodySnippet, url);
    }

    private String truncate(String content, int length) {
        if (content == null) return "";
        return content.length() > length ? content.substring(0, length) + "..." : content;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", " ");
    }
}
