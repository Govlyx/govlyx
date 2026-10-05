package com.govlyx.AI.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class SitemapGeneratorService {

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${sitemap.output.dir:/var/www/govlyx-frontend/sitemaps}")
    private String sitemapOutputDir;

    @Value("${app.base-url:https://govlyx.com}")
    private String baseUrl;

    /**
     * Runs every day at 3 AM to generate XML sitemaps directly to the disk.
     * This avoids expensive DB queries during bot crawling.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional(readOnly = true)
    public void generateSitemaps() {
        log.info("Starting background sitemap generation...");
        File dir = new File(sitemapOutputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("Failed to create sitemap directory: {}", sitemapOutputDir);
            return;
        }

        generateIssuesSitemap();
        generateQaSitemap();
        generatePincodesSitemap();
        generateCommunitiesSitemap();
        generateIndexSitemap();
        
        log.info("Sitemap generation completed successfully.");
    }

    private void generateIssuesSitemap() {
        try {
            // Using native queries as recommended in the architectural review for performance
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT id, content, updated_at FROM posts WHERE status IN ('ACTIVE', 'RESOLVED') ORDER BY updated_at DESC LIMIT 50000"
            ).getResultList();

            StringBuilder xml = startSitemap();
            for (Object[] row : results) {
                Long id = ((Number) row[0]).longValue();
                String content = (String) row[1];
                String lastmod = formatDate(row[2]);
                String slug = toSlug(content);
                xml.append(createUrlTag(baseUrl + "/issue/" + id + "/" + slug, lastmod, "0.8", "weekly"));
            }
            xml.append("</urlset>");
            writeToFile("sitemap-issues.xml", xml.toString());
        } catch (Exception e) {
            log.error("Error generating issues sitemap", e);
        }
    }

    private void generateQaSitemap() {
        try {
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT id, content, updated_at FROM social_posts WHERE status = 'ACTIVE' AND is_flagged = false ORDER BY updated_at DESC LIMIT 50000"
            ).getResultList();

            StringBuilder xml = startSitemap();
            for (Object[] row : results) {
                Long id = ((Number) row[0]).longValue();
                String content = (String) row[1];
                String lastmod = formatDate(row[2]);
                String slug = toSlug(content);
                xml.append(createUrlTag(baseUrl + "/q/" + id + "/" + slug, lastmod, "0.7", "weekly"));
            }
            xml.append("</urlset>");
            writeToFile("sitemap-qa.xml", xml.toString());
        } catch (Exception e) {
            log.error("Error generating QA sitemap", e);
        }
    }

    private void generatePincodesSitemap() {
        try {
            List<String> results = entityManager.createNativeQuery(
                    "SELECT pincode FROM pincode_lookup WHERE is_active = true"
            ).getResultList();

            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            StringBuilder xml = startSitemap();
            for (String pincode : results) {
                xml.append(createUrlTag(baseUrl + "/pincode/" + pincode, today, "0.6", "monthly"));
            }
            xml.append("</urlset>");
            writeToFile("sitemap-pincodes.xml", xml.toString());
        } catch (Exception e) {
            log.error("Error generating pincodes sitemap", e);
        }
    }

    private void generateCommunitiesSitemap() {
        try {
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT slug, updated_at FROM communities WHERE privacy = 'PUBLIC' AND status = 'ACTIVE' ORDER BY updated_at DESC LIMIT 50000"
            ).getResultList();

            StringBuilder xml = startSitemap();
            for (Object[] row : results) {
                String slug = (String) row[0];
                String lastmod = formatDate(row[1]);
                xml.append(createUrlTag(baseUrl + "/communities/" + slug, lastmod, "0.7", "weekly"));
            }
            xml.append("</urlset>");
            writeToFile("sitemap-communities.xml", xml.toString());
        } catch (Exception e) {
            log.error("Error generating communities sitemap", e);
        }
    }

    private void generateIndexSitemap() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String[] maps = {"sitemap-issues.xml", "sitemap-qa.xml", "sitemap-pincodes.xml", "sitemap-communities.xml"};
        for (String map : maps) {
            xml.append("  <sitemap>\n");
            xml.append("    <loc>").append(baseUrl).append("/").append(map).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("  </sitemap>\n");
        }
        xml.append("</sitemapindex>");
        try {
            writeToFile("sitemap.xml", xml.toString());
        } catch (Exception e) {
            log.error("Error generating index sitemap", e);
        }
    }

    private StringBuilder startSitemap() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        return sb;
    }

    /**
     * Builds a full sitemap <url> entry with lastmod, priority, and changefreq.
     *
     * @param url        The canonical URL of the page
     * @param lastmod    Date string in yyyy-MM-dd format (W3C datetime)
     * @param priority   Value between 0.0 and 1.0 indicating crawl priority
     * @param changefreq How often this URL changes (always/hourly/daily/weekly/monthly/yearly/never)
     */
    private String createUrlTag(String url, String lastmod, String priority, String changefreq) {
        return "  <url>\n" +
               "    <loc>" + url + "</loc>\n" +
               "    <lastmod>" + lastmod + "</lastmod>\n" +
               "    <changefreq>" + changefreq + "</changefreq>\n" +
               "    <priority>" + priority + "</priority>\n" +
               "  </url>\n";
    }

    /**
     * Safely converts a raw DB timestamp object (java.sql.Timestamp or java.time.LocalDateTime)
     * to a W3C yyyy-MM-dd string. Falls back to today's date if conversion fails.
     */
    private String formatDate(Object rawDate) {
        if (rawDate == null) return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            // Hibernate typically returns java.sql.Timestamp for native queries
            java.sql.Timestamp ts = (java.sql.Timestamp) rawDate;
            return ts.toLocalDateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (ClassCastException e) {
            // Fallback: try toString and parse first 10 chars (yyyy-MM-dd)
            String s = rawDate.toString();
            return s.length() >= 10 ? s.substring(0, 10) : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private void writeToFile(String filename, String content) throws IOException {
        File file = new File(sitemapOutputDir, filename);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        log.debug("Wrote sitemap file: {}", file.getAbsolutePath());
    }

    public static String toSlug(String content) {
        if (content == null || content.isEmpty()) return "post";
        String slug = content.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s]+", "-");
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }
}
