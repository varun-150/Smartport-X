package com.smartportx.notification.config;

import com.smartportx.notification.domain.Testimonial;
import com.smartportx.notification.domain.TestimonialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent demo-data seeder for testimonials (mirrors identity's DemoDataSeeder).
 * Skips seeding when the feature flag is OFF (test profile) or when rows already
 * exist (count &gt; 0), so restarts never duplicate content.
 */
@Component
public class TestimonialDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestimonialDataSeeder.class);

    private final TestimonialRepository repository;
    private final boolean enabled;

    public TestimonialDataSeeder(TestimonialRepository repository,
                                 @Value("${smartportx.notification.seed-testimonials:true}") boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Testimonial seeding disabled (seed-testimonials=false)");
            return;
        }
        if (repository.count() > 0) {
            log.info("Testimonials already seeded ({} rows present); skipping", repository.count());
            return;
        }
        List<Testimonial> testimonials = new ArrayList<>();
        add(testimonials, "Marina Costa", "Operations Director, Port of Rotterdam",
                "HarborFlow cut our yard dwell time by over 30% in the first quarter. " +
                        "Gate appointments and real-time container visibility let us plan with confidence.",
                5);
        add(testimonials, "James Whitfield", "Terminal Manager, Port of Singapore",
                "The dock-scheduling integration removed an entire shift of manual data entry. " +
                        "Discharge plans land in the dashboard before the ramp moves.",
                5);
        add(testimonials, "Aisha Rahman", "VP Port Operations, Jebel Ali",
                "Exception alerts reach the right controller in seconds. Vessel milestones used " +
                        "to live in three spreadsheets — now they are one live timeline.",
                5);
        add(testimonials, "Paul Meunier", "Head of Logistics, CMA CGM",
                "Our gate throughput improved because trucks no longer queue blindly. " +
                        "Quota visibility alone was worth the deployment.",
                4);
        add(testimonials, "Yuki Tanaka", "Chief Operating Officer, Port of Yokohama",
                "Reliable is the word. We restarted the platform after a power cut and the " +
                        "dock state, containers, and yard snapshots came back exactly as they were.",
                5);
        add(testimonials, "Sofia Lindqvist", "Port Director, Port of Gothenburg",
                "The REST-first design made integration with our legacy customs broker trivial. " +
                        "We track every handshake in one audit log.",
                4);
        add(testimonials, "Omar Haddad", "Supply Chain Lead, DP World",
                "Customs submission and the review loop are the smoothest I have used in 15 years " +
                        "of terminal ops. Fewer manual touchpoints, fewer errors.",
                5);
        add(testimonials, "Emily Chen", "IT Director, Port of Long Beach",
                "Role-based access put the right gates around operational reads without slowing " +
                        "down the controllers who need the numbers live.",
                4);
        add(testimonials, "Diego Fuentes", "Fleet Superintendent, Maersk",
                "Vessel ETA and berth scheduling sync keep our feeder loops honest. " +
                        "We plan our crew changes around live data, not guesswork.",
                5);
        add(testimonials, "Anna Kowalczyk", "Yard Supervisor, Port of Gdańsk",
                "Creating a movement or a stack slot takes seconds. The yard map finally " +
                        "represents what is actually on the ground.",
                4);
        add(testimonials, "Rajesh Nair", "Head of Gate Operations, Nhava Sheva",
                "The appointment preview with slot availability stopped the pre-dawn truck " +
                        "back-ups at our entrance. Operators love the quiet.",
                5);
        add(testimonials, "Luc Bélanger", "Quality Manager, Port of Montreal",
                "Three months in, zero data drift. The audit trail and problem responses make " +
                        "external audits almost pleasant — insurance asked for our API logs.",
                3);
        repository.saveAll(testimonials);
        log.info("Seeded {} testimonials", testimonials.size());
    }

    private void add(List<Testimonial> testimonials, String name, String role, String message, int rating) {
        Testimonial testimonial = new Testimonial();
        testimonial.setName(name);
        testimonial.setRole(role);
        testimonial.setMessage(message);
        testimonial.setRating(rating);
        testimonials.add(testimonial);
    }
}