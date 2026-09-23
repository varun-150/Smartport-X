package com.smartportx.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {

    List<Testimonial> findAllByOrderByCreatedAtDesc();
}