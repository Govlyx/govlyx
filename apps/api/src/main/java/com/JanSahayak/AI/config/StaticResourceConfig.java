package com.JanSahayak.AI.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@Slf4j
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:${user.home}/uploads/posts}")
    private String uploadDir;

    @Value("${app.social.upload.dir:${user.home}/uploads/social-posts}")
    private String socialUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Issue/Report posts upload handler
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/posts/**")
                .addResourceLocations("file:" + absolutePath + "/");

        log.info("Static resource handler configured for posts:");
        log.info("URL Pattern: /uploads/posts/**");
        log.info("File Location: file:{}/", absolutePath);

        // Social posts upload handler
        String socialAbsolutePath = Paths.get(socialUploadDir).toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/social-posts/**")
                .addResourceLocations("file:" + socialAbsolutePath + "/");

        log.info("Static resource handler configured for social posts:");
        log.info("URL Pattern: /uploads/social-posts/**");
        log.info("File Location: file:{}/", socialAbsolutePath);
    }
}
