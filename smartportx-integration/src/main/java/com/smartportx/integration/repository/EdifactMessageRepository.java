package com.smartportx.integration.repository;

import com.smartportx.integration.domain.EdifactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EdifactMessageRepository extends JpaRepository<EdifactMessage, UUID> {

    /** Idempotency (M8.5): find an already-processed interchange+message pair. */
    Optional<EdifactMessage> findBySenderAndReceiverAndInterchangeRefAndMessageRef(
            String sender, String receiver, String interchangeRef, String messageRef);
}
