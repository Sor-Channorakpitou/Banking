package com.example.bank.dto;

import com.example.bank.domain.AuditAction;
import com.example.bank.domain.AuditLog;
import com.example.bank.domain.AuditOutcome;

import java.time.Instant;

public record AuditLogResponse(Long id, Long actorUserId, AuditAction action, AuditOutcome outcome,
                               String targetType, String targetId, String details, String ipAddress,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getOutcome(),
                log.getTargetType(), log.getTargetId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
