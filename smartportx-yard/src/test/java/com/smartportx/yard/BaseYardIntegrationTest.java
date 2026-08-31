package com.smartportx.yard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.yard.domain.YardBay;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardRow;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.YardBayRepository;
import com.smartportx.yard.repository.YardBlockRepository;
import com.smartportx.yard.repository.YardRowRepository;
import com.smartportx.yard.repository.YardTierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {YardTestSecurityConfig.class, YardTestSupport.class})
public abstract class BaseYardIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected YardBlockRepository blockRepository;

    @Autowired
    protected YardBayRepository bayRepository;

    @Autowired
    protected YardRowRepository rowRepository;

    @Autowired
    protected YardTierRepository tierRepository;

    protected YardBlock block(String code) {
        return blockRepository.findByBlockCode(code).orElseThrow();
    }

    protected YardBay firstBay(UUID blockId) {
        List<YardBay> bays = bayRepository.findByBlockIdOrderByBayNumberAsc(blockId);
        return bays.get(0);
    }

    protected YardRow firstRow(UUID bayId) {
        return rowRepository.findByBayIdOrderByRowNumberAsc(bayId).get(0);
    }

    protected YardTier firstTier(UUID rowId) {
        return tierRepository.findByRowIdOrderByTierNumberAsc(rowId).get(0);
    }

    protected String tierUrl(UUID blockId, UUID bayId, UUID rowId, UUID tierId) {
        return "/api/v1/yard/blocks/" + blockId + "/bays/" + bayId + "/rows/" + rowId + "/tiers/" + tierId
                + "/container";
    }

    protected Map<String, Object> putMessage(UUID containerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("containerId", containerId);
        return body;
    }

    protected ResultActions performPost(String url, String body, String token) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions performGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions performPut(String url, String body, String token) throws Exception {
        return mockMvc.perform(put(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions performDelete(String url, String token) throws Exception {
        return mockMvc.perform(delete(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}