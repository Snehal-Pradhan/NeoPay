package com.snehal.neopay.merchant.api;

import com.snehal.neopay.common.cache.ApiKeyCache;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MerchantApiTest.TestSecurity.class)
class MerchantApiTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyCache apiKeyCache;

    @Test
    void signupLoginAndApiKeyLifecycle() throws Exception {
        String email = "lifecycle-" + System.nanoTime() + "@example.com";
        String merchantId = signup(email);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        MvcResult created = mockMvc.perform(post("/v1/merchants/api-keys")
                        .header("X-Merchant-Id", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"TEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyId").value(org.hamcrest.Matchers.startsWith("np_test_")))
                .andReturn();

        String keyId = JsonPath.read(created.getResponse().getContentAsString(), "$.keyId");
        String apiKeyId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/v1/merchants/api-keys").header("X-Merchant-Id", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].keyId").value(keyId));

        mockMvc.perform(post("/v1/merchants/api-keys/" + apiKeyId + "/rotate")
                        .header("X-Merchant-Id", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keySecret").isNotEmpty());

        mockMvc.perform(delete("/v1/merchants/api-keys/" + apiKeyId)
                        .header("X-Merchant-Id", merchantId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/internal/api-keys/" + keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void webhookConfigCrud() throws Exception {
        String merchantId = signup("webhook-" + System.nanoTime() + "@example.com");
        String body = "{\"targetUrl\":\"https://example.com/hook\",\"eventTypes\":\"ALL\"}";

        MvcResult created = mockMvc.perform(post("/v1/merchants/webhooks")
                        .header("X-Merchant-Id", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/hook"))
                .andReturn();

        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/v1/merchants/webhooks").header("X-Merchant-Id", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));

        mockMvc.perform(get("/v1/merchants/webhooks/" + id).header("X-Merchant-Id", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(put("/v1/merchants/webhooks/" + id)
                        .header("X-Merchant-Id", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/v2\",\"eventTypes\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/v2"));

        mockMvc.perform(delete("/v1/merchants/webhooks/" + id)
                        .header("X-Merchant-Id", merchantId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/merchants/webhooks/" + id).header("X-Merchant-Id", merchantId))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationFailureReturnsProblemDetail() throws Exception {
        String merchantId = signup("validation-" + System.nanoTime() + "@example.com");

        mockMvc.perform(post("/v1/merchants/webhooks")
                        .header("X-Merchant-Id", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void unknownApiKeyReturnsNotFound() throws Exception {
        mockMvc.perform(get("/internal/api-keys/np_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("APIKEY_NOT_FOUND"));
    }

    private String signup(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API Test\",\"email\":\"" + email
                                + "\",\"password\":\"password123\",\"businessType\":\"LLP\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertNotNull(id);
        return id;
    }
}
