package com.eclectics.collaboration.Tool.dto;

import lombok.Builder;

@Builder
public record NotificationPreferenceResponse(
        boolean emailOnAssign, boolean inAppOnAssign,
        boolean emailOnBoardAdd, boolean inAppOnBoardAdd,
        boolean emailOnMention, boolean inAppOnMention,
        boolean emailOnDueSoon, boolean inAppOnDueSoon,
        boolean weeklyDigest
) {}
