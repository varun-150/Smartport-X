package com.smartportx.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "roles", schema = "smartportx_identity")
public class Role {

    @Id
    private UUID id;

    @Column(length = 20, nullable = false, unique = true)
    private String name;

    @Column(length = 255)
    private String description;

    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}