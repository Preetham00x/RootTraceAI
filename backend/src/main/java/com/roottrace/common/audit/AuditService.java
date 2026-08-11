package com.roottrace.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(AuditEventType eventType, String entityType,
                       String entityId, String actor, String details) {
        AuditEvent event = AuditEvent.create(eventType, entityType, entityId, actor, details);
        auditEventRepository.save(event);
        log.debug("Audit event recorded: {} on {}:{} by {}",
                eventType, entityType, entityId, actor);
    }
}
