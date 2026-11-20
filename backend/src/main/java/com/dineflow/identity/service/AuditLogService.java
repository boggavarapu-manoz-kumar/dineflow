package com.dineflow.identity.service;

import com.dineflow.identity.domain.LoginAuditLogEntity;
import com.dineflow.identity.repository.LoginAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private final LoginAuditLogRepository loginAuditLogRepository;

    public AuditLogService(LoginAuditLogRepository loginAuditLogRepository) {
        this.loginAuditLogRepository = loginAuditLogRepository;
    }

    @Transactional
    public void recordAuthEvent(String email, UUID tenantId, String outcome, String failureReason, String ip, String ua) {
        LoginAuditLogEntity audit = new LoginAuditLogEntity();
        audit.setEmail(email != null ? email : "UNKNOWN");
        audit.setTenantId(tenantId);
        audit.setOutcome(outcome);
        audit.setFailureReason(failureReason);
        audit.setIpAddress(ip);
        audit.setUserAgent(ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua);
        loginAuditLogRepository.save(audit);
    }
}