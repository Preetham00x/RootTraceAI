package com.roottrace.postmortem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostmortemActionItemRepository extends JpaRepository<PostmortemActionItem, UUID> {

    List<PostmortemActionItem> findByPostmortemId(UUID postmortemId);

    Optional<PostmortemActionItem> findByIdAndPostmortemId(UUID id, UUID postmortemId);
}
