package com.JanSahayak.AI.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RobotsController {

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRobotsTxt() {
        String content = """
                User-agent: *

                # Public landing and informational pages
                Allow: /$
                Allow: /login$
                Allow: /register$
                Allow: /privacy-policy$
                Allow: /how-to-use$
                Allow: /review$
                Allow: /upcoming-updates$

                # Public community and content routes
                Allow: /communities/
                Allow: /post/

                # SEO-indexed hyperlocal routes
                Allow: /pincode/
                Allow: /issue/
                Allow: /q/
                Allow: /city/
                Allow: /dept/
                Allow: /category/

                # Blocked private app routes
                Disallow: /dashboard
                Disallow: /settings
                Disallow: /admin/
                Disallow: /profile
                Disallow: /quick-chat
                Disallow: /notifications
                Disallow: /search
                Disallow: /api/
                Disallow: /actuator/

                Sitemap: https://govlyx.com/sitemap.xml
                """;
        return ResponseEntity.ok(content);
    }
}
