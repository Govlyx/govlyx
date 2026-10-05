package com.govlyx.AI.config;

import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.UserRepo;
import com.govlyx.AI.security.JwtUtil;
import com.govlyx.AI.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import com.govlyx.AI.util.IpUtils;
import java.util.Map;

import java.security.Principal;

/**
 * WebSocket configuration + disconnect handler.
 *
 * ── Principal name ────────────────────────────────────────────────────────────
 * The STOMP principal is stored as:
 *   new UsernamePasswordAuthenticationToken(user, null, authorities)
 * so Authentication.getName() == user.getUsername() == EMAIL.
 *
 * convertAndSendToUser(email, dest, payload) therefore routes correctly, and
 * @AuthenticationPrincipal resolves to the actual User entity.
 *
 * ── Disconnect handling ───────────────────────────────────────────────────────
 * This class also implements ApplicationListener<SessionDisconnectEvent>.
 * Spring calls onApplicationEvent() automatically whenever any WebSocket
 * session closes (refresh, tab close, network drop).
 * event.getUser().getName() == email → passed to handleUserDisconnectByEmail().
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer,
        ApplicationListener<SessionDisconnectEvent> {

    private final JwtUtil            jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepo           userRepo;
    private final ChatSessionService chatSessionService;

    public WebSocketConfig(
            JwtUtil jwtUtil,
            @Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
            UserRepo userRepo,
            ChatSessionService chatSessionService
    ) {
        this.jwtUtil             = jwtUtil;
        this.userDetailsService  = userDetailsService;
        this.userRepo            = userRepo;
        this.chatSessionService  = chatSessionService;
    }

    // ── Broker & endpoints ────────────────────────────────────────────────────

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        if (request instanceof ServletServerHttpRequest) {
                            jakarta.servlet.http.HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
                            String ipAddress = IpUtils.extractIp(servletRequest);
                            if (ipAddress != null) {
                                attributes.put("WS_CLIENT_IP", ipAddress);
                            }
                        }
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .withSockJS();
    }

    // ── Inbound channel interceptor (JWT auth on CONNECT) ────────────────────

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.debug("WebSocket CONNECT without Authorization header — rejected gracefully");
                    return null;
                }

                String token = authHeader.substring(7);

                try {
                    if (jwtUtil.isTokenExpired(token)) {
                        log.warn("WebSocket CONNECT rejected: token expired");
                        return null;
                    }
                    if (!jwtUtil.validateToken(token)) {
                        log.warn("WebSocket CONNECT rejected: invalid token");
                        return null;
                    }

                    String usernameFromJwt = jwtUtil.getUsernameFromToken(token);
                    if (usernameFromJwt == null) {
                        log.warn("WebSocket CONNECT rejected: token has no subject");
                        return null;
                    }

                    // JWT subject = USERNAME; look up by username (not email)
                    User user = userRepo.findByUsername(usernameFromJwt).orElse(null);
                    if (user == null) {
                        log.warn("WebSocket CONNECT rejected: user not found for {}", usernameFromJwt);
                        return null;
                    }

                    // Store User as principal so that:
                    //   @AuthenticationPrincipal User user  → resolves correctly ✓
                    //   Authentication.getName()            → user.getUsername() == email ✓
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );

                    accessor.setUser(auth);

                    log.info("WebSocket authenticated: username={} principalName(email)={}",
                            usernameFromJwt, user.getEmail());

                    String ipAddress = null;
                    if (accessor.getSessionAttributes() != null) {
                        ipAddress = (String) accessor.getSessionAttributes().get("WS_CLIENT_IP");
                    }
                    chatSessionService.trackUserIp(user.getId(), ipAddress);

                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    log.warn("WebSocket CONNECT rejected: token expired");
                    return null;
                } catch (Exception e) {
                    log.error("WebSocket authentication failed: {}", e.getMessage());
                    return null;
                }

                return message;
            }
        });
    }

    // ── WebSocket disconnect handler ──────────────────────────────────────────

    /**
     * Called automatically by Spring whenever a WebSocket session closes.
     *
     * event.getUser().getName() == email (because the principal was stored as
     * a UsernamePasswordAuthenticationToken(user, ...) and
     * user.getUsername() returns EMAIL in this project).
     *
     * Delegates to ChatSessionService.handleUserDisconnectByEmail() which
     * starts the reconnect grace period so a page-refresh does NOT hard-end
     * the chat session immediately.
     */
    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) {
            log.debug("WebSocket disconnect with no principal — ignoring");
            return;
        }

        String email = principal.getName(); // == user.getUsername() == email
        log.info("WebSocket disconnected: principal(email)={}", email);

        try {
            chatSessionService.handleUserDisconnectByEmail(email);
        } catch (Exception e) {
            log.error("Error handling WebSocket disconnect for {}", email, e);
        }
    }
}
