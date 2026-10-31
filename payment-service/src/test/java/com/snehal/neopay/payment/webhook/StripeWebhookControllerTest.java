package com.snehal.neopay.payment.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookControllerTest {

    private final StripeWebhookService service = mock(StripeWebhookService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new StripeWebhookController(service))
            .build();

    @Test
    void signatureHeaderIsForwardedAndOutcomeReturned() throws Exception {
        when(service.receive("{\"id\":\"evt_1\"}", "t=1,v1=abc"))
                .thenReturn(new StripeWebhookService.WebhookOutcome("evt_1", "PROCESSED", false));

        mockMvc.perform(post("/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(StripeWebhookVerifier.SIGNATURE_HEADER, "t=1,v1=abc")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt_1"))
                .andExpect(jsonPath("$.duplicate").value(false));

        verify(service).receive("{\"id\":\"evt_1\"}", "t=1,v1=abc");
    }

    @Test
    void absentHeaderIsForwardedAsNull() throws Exception {
        when(service.receive(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new StripeWebhookService.WebhookOutcome("evt_1", "FAILED", false));

        mockMvc.perform(post("/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk());
    }
}
