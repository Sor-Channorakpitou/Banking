package com.example.bank.controller;

import com.example.bank.dto.AuditLogResponse;
import com.example.bank.dto.PageResponse;
import com.example.bank.service.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Newest first; optionally only one user's actions. */
    @GetMapping
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) Long actorUserId,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return auditService.list(actorUserId, page, size);
    }
}
