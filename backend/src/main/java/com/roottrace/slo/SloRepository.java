package com.roottrace.slo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SloRepository extends JpaRepository<Slo, UUID> {

    List<Slo> findByServiceNameAndEnabledTrue(String serviceName);

    List<Slo> findByServiceName(String serviceName);

    Optional<Slo> findByServiceNameAndName(String serviceName, String name);

    Optional<Slo> findByIdAndServiceName(UUID id, String serviceName);

    List<Slo> findByEnabledTrue();
}
