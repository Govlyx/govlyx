package com.JanSahayak.AI.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

@Configuration
@EnableConfigurationProperties
public class MultipartConfig {

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        // Set file size limits
        factory.setMaxFileSize(DataSize.ofMegabytes(512));
        factory.setMaxRequestSize(DataSize.ofMegabytes(512));

        // Set location for temporary files
        factory.setLocation(System.getProperty("java.io.tmpdir"));

        return factory.createMultipartConfig();
    }
}
