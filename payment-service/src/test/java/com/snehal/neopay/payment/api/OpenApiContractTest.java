package com.snehal.neopay.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsExposePaymentContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").exists())
                .andExpect(jsonPath("$.paths['/v1/orders']").exists())
                .andExpect(jsonPath("$.paths['/v1/payments']").exists())
                .andExpect(jsonPath("$.paths['/v1/payments/{paymentId}/capture']").exists())
                .andReturn();

        if (Boolean.getBoolean("openapi.write")) {
            java.nio.file.Path output = java.nio.file.Path.of("../docs/openapi/payment-service.json");
            java.nio.file.Files.createDirectories(output.getParent());
            java.nio.file.Files.writeString(output,
                    result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
