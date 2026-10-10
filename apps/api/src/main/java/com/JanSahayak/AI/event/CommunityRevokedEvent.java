package com.JanSahayak.AI.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommunityRevokedEvent extends ApplicationEvent {
    private final Long communityId;
    private final Long requesterId;

    public CommunityRevokedEvent(Object source, Long communityId, Long requesterId) {
        super(source);
        this.communityId = communityId;
        this.requesterId = requesterId;
    }
}
