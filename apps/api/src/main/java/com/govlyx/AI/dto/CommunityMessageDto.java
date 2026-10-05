package com.govlyx.AI.dto;

import com.govlyx.AI.model.CommunityMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMessageDto implements Serializable {

    private Long id;
    private Long communityId;
    private String content;
    private String messageType;
    private AuthorDto sender;
    private SocialPostDto sharedPost;
    private Long replyToId;
    private boolean isDeleted;
    private String deletedByType;
    private boolean isEdited;
    private boolean isPinned;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Converts a CommunityMessage entity to DTO. Eagerly extracts properties 
     * using safe checks to prevent lazy loading issues outside transaction scope.
     */
    public static CommunityMessageDto fromEntity(CommunityMessage msg) {
        if (msg == null) return null;

        String senderRole = null;
        try {
            if (msg.getSender() != null && msg.getSender().getRole() != null) {
                senderRole = msg.getSender().getRole().getName();
            }
        } catch (Exception ignored) {
            // Safe guard against closed session context
        }

        SocialPostDto sharedPostDto = null;
        try {
            if (msg.getSharedPost() != null) {
                try {
                    sharedPostDto = SocialPostDto.fromSocialPost(msg.getSharedPost());
                } catch (Exception e) {
                    // Fallback to snapshot if proxy throws EntityNotFoundException (post deleted)
                    if (msg.getSharedPostSnapshot() != null) {
                        sharedPostDto = buildFromSnapshot(msg.getSharedPostSnapshot());
                    }
                }
            } else if (msg.getSharedPostSnapshot() != null) {
                sharedPostDto = buildFromSnapshot(msg.getSharedPostSnapshot());
            }
        } catch (Exception ignored) {
            // Safe guard against uninitialized sharedPost proxy context
        }

        return CommunityMessageDto.builder()
                .id(msg.getId())
                .communityId(msg.getCommunityId())
                .content(msg.isDeleted() ? null : msg.getContent())
                .messageType(msg.getMessageType() != null ? msg.getMessageType().name() : null)
                .sender(AuthorDto.fromUser(msg.getSender(), senderRole))
                .sharedPost(sharedPostDto)
                .replyToId(msg.getReplyToId())
                .isDeleted(msg.isDeleted())
                .deletedByType(msg.getDeletedByType())
                .isEdited(msg.isEdited())
                .isPinned(msg.isPinned())
                .createdAt(msg.getCreatedAt())
                .expiresAt(msg.getExpiresAt())
                .build();
    }

    private static SocialPostDto buildFromSnapshot(com.govlyx.AI.model.CommunitySharedPostSnapshot snapshot) {
        return SocialPostDto.builder()
                .id(snapshot.getPostId())
                .content(snapshot.getContent())
                .author(AuthorDto.builder()
                        .username(snapshot.getAuthorUsername())
                        .profileImage(snapshot.getAuthorAvatar())
                        .build())
                .build();
    }
}
