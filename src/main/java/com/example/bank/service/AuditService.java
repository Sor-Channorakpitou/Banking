package com.example.bank.service;

import com.example.bank.domain.AuditAction;
import com.example.bank.domain.AuditLog;
import com.example.bank.domain.AuditOutcome;
import com.example.bank.dto.AuditLogResponse;
import com.example.bank.dto.PageResponse;
import com.example.bank.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records user actions. {@code record} joins the caller's transaction when there is
 * one, so a successful action and its audit row commit together, or not at all.
 * Failures such as a bad login are recorded from code that has no surrounding
 * transaction, so their audit row commits on its own and survives the error.
 */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(AuditAction action, AuditOutcome outcome, Long actorUserId,
                       String targetType, Object targetId, String details) {
        String safeDetails = details != null && details.length() > MAX_DETAILS
                ? details.substring(0, MAX_DETAILS) : details;
        auditLogRepository.save(new AuditLog(actorUserId, action, outcome, targetType,
                targetId == null ? null : targetId.toString(), safeDetails, currentIpAddress()));
    }

    @Transactional
    public void success(AuditAction action, Long actorUserId, String targetType, Object targetId, String details) {
        record(action, AuditOutcome.SUCCESS, actorUserId, targetType, targetId, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(Long actorUserId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return PageResponse.from((actorUserId == null
                ? auditLogRepository.findAllByOrderByIdDesc(pageable)
                : auditLogRepository.findByActorUserIdOrderByIdDesc(actorUserId, pageable))
                .map(AuditLogResponse::from));
    }

    /**
     * The client IP when called during an HTTP request, otherwise null (e.g. startup
     * jobs). Behind a proxy, set server.forward-headers-strategy so this is the real
     * client address and not the proxy's.
     */
    private static String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
