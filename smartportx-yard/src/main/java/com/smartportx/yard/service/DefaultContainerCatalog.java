package com.smartportx.yard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(ContainerCatalog.class)
public class DefaultContainerCatalog implements ContainerCatalog {

    private static final Logger log = LoggerFactory.getLogger(DefaultContainerCatalog.class);

    @Override
    public Optional<ContainerInfo> findById(UUID containerId) {
        log.warn("Container catalog expected from container service, but no catalog is wired "
                + "(client lands in M5.7); degrading to non-dangerous/no-weight for container {}",
                containerId);
        return Optional.empty();
    }
}