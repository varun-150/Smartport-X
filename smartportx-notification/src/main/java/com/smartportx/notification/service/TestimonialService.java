package com.smartportx.notification.service;

import com.smartportx.common.error.NotFoundException;
import com.smartportx.notification.domain.Testimonial;
import com.smartportx.notification.domain.TestimonialRepository;
import com.smartportx.notification.web.dto.TestimonialRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TestimonialService {

    private final TestimonialRepository repository;

    public TestimonialService(TestimonialRepository repository) {
        this.repository = repository;
    }

    public List<Testimonial> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Testimonial get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Testimonial " + id + " not found"));
    }

    @Transactional
    public Testimonial create(TestimonialRequest request) {
        Testimonial testimonial = new Testimonial();
        apply(testimonial, request);
        return repository.save(testimonial);
    }

    @Transactional
    public Testimonial update(UUID id, TestimonialRequest request) {
        Testimonial testimonial = get(id);
        apply(testimonial, request);
        return repository.save(testimonial);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    private void apply(Testimonial testimonial, TestimonialRequest request) {
        testimonial.setName(request.name());
        testimonial.setRole(request.role());
        testimonial.setMessage(request.message());
        testimonial.setRating(request.rating());
    }
}