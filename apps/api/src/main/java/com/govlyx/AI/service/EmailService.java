package com.Govlyx.AI.service;

import com.Govlyx.AI.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${app.frontend.base-url:https://govlyx.com}")
    private String frontendBaseUrl;

    @Value("${spring.mail.username:govlyx.official@gmail.com}")
    private String fromEmail;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.Govlyx.AI.security.AesGcmEmailConverter emailConverter;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private String resolveRecipientEmail(User user) {
        if (user == null) return null;
        return user.getDecryptedEmail(emailConverter);
    }

    @Async
    public void sendVerificationEmail(User user, String token) {
        String verificationUrl = frontendBaseUrl + "/verify-email?token=" + token;
        String toEmail = resolveRecipientEmail(user);
        log.info("Preparing to send email verification link via Brevo to {}: {}", toEmail, verificationUrl);
        log.info("[EMAIL VERIFICATION LINK] User={} Link={}", toEmail, verificationUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            Map<String, Object> sender = new HashMap<>();
            sender.put("name", "Govlyx Portal");
            sender.put("email", fromEmail);

            Map<String, Object> to = new HashMap<>();
            to.put("email", toEmail);
            if (user.getActualUsername() != null && !user.getActualUsername().trim().isEmpty()) {
                to.put("name", user.getActualUsername());
            }

            String htmlContent = buildHtmlTemplate(user.getActualUsername(), verificationUrl);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sender", sender);
            requestBody.put("to", List.of(to));
            requestBody.put("subject", "Verify your Govlyx Account");
            requestBody.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Verification email successfully sent to {} via Brevo API", toEmail);
            } else {
                log.error("Failed to send verification email. Brevo API response: {}", response.getBody());
            }

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("[BREVO API ERROR] Failed to send verification email to {}. Status: {}, Response: {}", 
                    toEmail, e.getStatusCode(), responseBody);
            if (responseBody.contains("authorised_ips") || responseBody.contains("unrecognised IP address")) {
                log.warn("[BREVO AUTHORIZED IPS REQUIRED] Brevo blocked this request because the IP is not in your Brevo Authorized IPs list. "
                        + "Please whitelist your IP at https://app.brevo.com/security/authorised_ips or disable IP restrictions in Brevo.");
            }
        } catch (Exception e) {
            log.error("Unexpected error while sending verification email via Brevo to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private String buildHtmlTemplate(String username, String verificationUrl) {
        String safeUsername = org.springframework.web.util.HtmlUtils.htmlEscape(username != null ? username : "");
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "  <meta charset='utf-8'>"
                + "  <style>"
                + "    body { font-family: 'Inter', sans-serif; background-color: #f4f5f7; margin: 0; padding: 0; }"
                + "    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 16px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); overflow: hidden; border: 1px solid #e1e4e8; }"
                + "    .header { background: #1d4ed8; color: #ffffff; padding: 32px; text-align: center; }"
                + "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }"
                + "    .content { padding: 40px 32px; color: #374151; line-height: 1.6; }"
                + "    .content h2 { margin-top: 0; color: #111827; font-size: 20px; font-weight: 600; }"
                + "    .btn-container { text-align: center; margin: 32px 0; }"
                + "    .btn { display: inline-block; background-color: #1d4ed8; color: #ffffff !important; text-decoration: none; padding: 14px 28px; font-weight: 600; border-radius: 12px; font-size: 16px; box-shadow: 0 4px 6px rgba(29, 78, 216, 0.15); transition: background-color 0.2s; }"
                + "    .footer { background: #f9fafb; padding: 24px 32px; text-align: center; color: #6b7280; font-size: 13px; border-top: 1px solid #f3f4f6; }"
                + "    .footer a { color: #1d4ed8; text-decoration: none; }"
                + "  </style>"
                + "</head>"
                + "<body>"
                + "  <div class='container'>"
                + "    <div class='header'>"
                + "      <h1>Govlyx Portal</h1>"
                + "    </div>"
                + "    <div class='content'>"
                + "      <h2>Welcome, " + safeUsername + "!</h2>"
                + "      <p>Thank you for joining Govlyx. To complete your registration and activate your account, please verify your email address by clicking the button below:</p>"
                + "      <div class='btn-container'>"
                + "        <a href='" + verificationUrl + "' class='btn'>Verify Email Address</a>"
                + "      </div>"
                + "      <p>If the button doesn't work, you can copy and paste the following link into your browser:</p>"
                + "      <p style='word-break: break-all; color: #2563eb; font-size: 14px;'>" + verificationUrl + "</p>"
                + "      <p>This verification link will expire in 24 hours.</p>"
                + "    </div>"
                + "    <div class='footer'>"
                + "      <p>This is an automated message, please do not reply directly to this email.</p>"
                + "      <p>&copy; 2026 Govlyx. All rights reserved.</p>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }

    @Async
    public void sendCopyrightTakedownEmail(User user, String postContent, int strikes, String reason) {
        log.info("Preparing copyright takedown email for {}", user.getEmail());
        String htmlContent = "<p>Dear " + user.getActualUsername() + ",</p>"
                + "<p>One of your posts has been removed due to a copyright infringement claim:</p>"
                + "<blockquote>" + postContent + "</blockquote>"
                + "<p><strong>Reason:</strong> " + reason + "</p>"
                + "<p>You currently have <strong>" + strikes + " copyright strike(s)</strong>.</p>"
                + "<p>Accumulating 3 strikes will result in permanent account suspension.</p>";
        sendEmail(resolveRecipientEmail(user), user.getActualUsername(), "Copyright Takedown Notice", htmlContent);
    }

    @Async
    public void sendCopyrightBanEmail(User user, String reason) {
        log.info("Preparing copyright ban email for {}", user.getEmail());
        String htmlContent = "<p>Dear " + user.getActualUsername() + ",</p>"
                + "<p>Your account has been permanently suspended due to repeated copyright violations (3 strikes).</p>"
                + "<p><strong>Reason:</strong> " + reason + "</p>";
        sendEmail(resolveRecipientEmail(user), user.getActualUsername(), "Account Suspended - Copyright Violations", htmlContent);
    }

    @Async
    public void sendExternalClaimConfirmationEmail(String claimantEmail, String claimantName, String referenceId) {
        log.info("Preparing external claim confirmation email for {}", claimantEmail);
        String htmlContent = "<p>Dear " + claimantName + ",</p>"
                + "<p>We have received your copyright infringement claim.</p>"
                + "<p>Your reference ID is: <strong>" + referenceId + "</strong></p>"
                + "<p>Our Grievance Officer will review your claim and acknowledge it within 24 hours.</p>";
        sendEmail(claimantEmail, claimantName, "Copyright Claim Received - " + referenceId, htmlContent);
    }

    @Async
    public void sendExternalClaimAcknowledgedEmail(String claimantEmail, String claimantName, String referenceId) {
        log.info("Preparing external claim acknowledged email for {}", claimantEmail);
        String htmlContent = "<p>Dear " + claimantName + ",</p>"
                + "<p>Your copyright infringement claim (Reference: <strong>" + referenceId + "</strong>) has been acknowledged.</p>"
                + "<p>Our team is currently reviewing the claim and will take appropriate action as per the Information Technology (Intermediary Guidelines) Rules, 2021.</p>";
        sendEmail(claimantEmail, claimantName, "Copyright Claim Acknowledged - " + referenceId, htmlContent);
    }

    private void sendEmail(String toEmail, String toName, String subject, String htmlContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            Map<String, Object> sender = new HashMap<>();
            sender.put("name", "Govlyx Trust & Safety");
            sender.put("email", fromEmail);

            Map<String, Object> to = new HashMap<>();
            to.put("email", toEmail);
            if (toName != null && !toName.isEmpty()) {
                to.put("name", toName);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sender", sender);
            requestBody.put("to", List.of(to));
            requestBody.put("subject", subject);
            requestBody.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully to {}", toEmail);
            } else {
                log.error("Failed to send email. Response: {}", response.getBody());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("[BREVO API ERROR] Failed to send email to {}. Status: {}, Response: {}", 
                    toEmail, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendEmailUpdateAlertEmail(User user) {
        log.info("Preparing to send email update alert to {}: {}", user.getEmail(), user.getEmail());
        String htmlContent = "<p>Dear " + user.getActualUsername() + ",</p>"
                + "<p>We received a request to change the email address associated with your Govlyx account.</p>"
                + "<p>If you made this request, no further action is required here. Please check your new email inbox to verify the change.</p>"
                + "<p><strong>If you did not request this change, please secure your account immediately and contact support.</strong></p>";
        sendEmail(resolveRecipientEmail(user), user.getActualUsername(), "Security Alert: Email Change Requested", htmlContent);
    }

    @Async
    public void sendEmailUpdateVerificationEmail(User user, String token, String pendingEmail) {
        String verificationUrl = frontendBaseUrl + "/verify-email-update?token=" + token;
        log.info("Preparing to send email update verification link to {}: {}", pendingEmail, verificationUrl);
        String safeUsername = org.springframework.web.util.HtmlUtils.htmlEscape(user.getActualUsername() != null ? user.getActualUsername() : "");
        String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "  <meta charset='utf-8'>"
                + "  <style>"
                + "    body { font-family: 'Inter', sans-serif; background-color: #f4f5f7; margin: 0; padding: 0; }"
                + "    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 16px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); overflow: hidden; border: 1px solid #e1e4e8; }"
                + "    .header { background: #1d4ed8; color: #ffffff; padding: 32px; text-align: center; }"
                + "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }"
                + "    .content { padding: 40px 32px; color: #374151; line-height: 1.6; }"
                + "    .content h2 { margin-top: 0; color: #111827; font-size: 20px; font-weight: 600; }"
                + "    .btn-container { text-align: center; margin: 32px 0; }"
                + "    .btn { display: inline-block; background-color: #1d4ed8; color: #ffffff !important; text-decoration: none; padding: 14px 28px; font-weight: 600; border-radius: 12px; font-size: 16px; box-shadow: 0 4px 6px rgba(29, 78, 216, 0.15); transition: background-color 0.2s; }"
                + "    .footer { background: #f9fafb; padding: 24px 32px; text-align: center; color: #6b7280; font-size: 13px; border-top: 1px solid #f3f4f6; }"
                + "    .footer a { color: #1d4ed8; text-decoration: none; }"
                + "  </style>"
                + "</head>"
                + "<body>"
                + "  <div class='container'>"
                + "    <div class='header'>"
                + "      <h1>Govlyx Portal</h1>"
                + "    </div>"
                + "    <div class='content'>"
                + "      <h2>Hello, " + safeUsername + "!</h2>"
                + "      <p>You recently requested to update your email address. Please confirm this change by clicking the button below:</p>"
                + "      <div class='btn-container'>"
                + "        <a href='" + verificationUrl + "' class='btn'>Confirm New Email Address</a>"
                + "      </div>"
                + "      <p>If the button doesn't work, you can copy and paste the following link into your browser:</p>"
                + "      <p style='word-break: break-all; color: #2563eb; font-size: 14px;'>" + verificationUrl + "</p>"
                + "      <p>This verification link will expire in 24 hours.</p>"
                + "    </div>"
                + "    <div class='footer'>"
                + "      <p>This is an automated message, please do not reply directly to this email.</p>"
                + "      <p>&copy; 2026 Govlyx. All rights reserved.</p>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
        sendEmail(pendingEmail, user.getActualUsername(), "Verify your new Email Address", htmlContent);
    }
}
