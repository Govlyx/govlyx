package com.govlyx.AI.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommunityDeletedEvent extends ApplicationEvent {
    private final Long communityId;
    private final Long requesterId;

    public CommunityDeletedEvent(Object source, Long communityId, Long requesterId) {
        super(source);
        this.communityId = communityId;
        this.requesterId = requesterId;
    }
}
