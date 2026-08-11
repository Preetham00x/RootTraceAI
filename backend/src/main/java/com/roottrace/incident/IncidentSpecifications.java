package com.roottrace.incident;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specifications for dynamic Incident filtering.
 * All specifications automatically exclude soft-deleted records.
 */
final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    static Specification<Incident> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate statusMatch = cb.equal(root.get("status"), status);
            return cb.and(notDeleted, statusMatch);
        };
    }

    static Specification<Incident> hasSeverity(IncidentSeverity severity) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate severityMatch = cb.equal(root.get("severity"), severity);
            return cb.and(notDeleted, severityMatch);
        };
    }

    static Specification<Incident> hasService(String service) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate serviceMatch = cb.equal(cb.lower(root.get("service")), service.toLowerCase());
            return cb.and(notDeleted, serviceMatch);
        };
    }

    static Specification<Incident> hasEnvironment(String environment) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate envMatch = cb.equal(cb.lower(root.get("environment")), environment.toLowerCase());
            return cb.and(notDeleted, envMatch);
        };
    }

    static Specification<Incident> hasCreatedBy(String createdBy) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate createdByMatch = cb.equal(root.get("createdBy"), createdBy);
            return cb.and(notDeleted, createdByMatch);
        };
    }

    static Specification<Incident> titleContains(String search) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate titleMatch = cb.like(cb.lower(root.get("title")),
                    "%" + search.toLowerCase() + "%");
            return cb.and(notDeleted, titleMatch);
        };
    }
}
