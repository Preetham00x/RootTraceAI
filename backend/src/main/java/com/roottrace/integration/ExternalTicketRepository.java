package com.roottrace.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalTicketRepository extends JpaRepository<ExternalTicket, UUID> {

    List<ExternalTicket> findByIncidentId(UUID incidentId);

    Optional<ExternalTicket> findByProviderAndActionItemId(String provider, UUID actionItemId);
}
