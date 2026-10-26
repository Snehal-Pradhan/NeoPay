package com.snehal.neopay.operations.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestClientWebhookSenderTest {

    @Test
    void postsSignatureHeaderAndReturnsStatusCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://merchant.example/hook"))
                .andExpect(header("X-NeoPay-Signature", "sig-1"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        RestClientWebhookSender sender = new RestClientWebhookSender(builder.build());
        ReflectionTestUtils.setField(sender, "signatureHeader", "X-NeoPay-Signature");

        int status = sender.post("https://merchant.example/hook", "sig-1",
                Map.of("event", "payment.captured"));

        assertEquals(202, status);
        server.verify();
    }

    @Test
    void customHeaderIsHonoured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://merchant.example/hook"))
                .andExpect(header("X-Custom-Signature", "sig-2"))
                .andRespond(withStatus(HttpStatus.OK));
        RestClientWebhookSender sender = new RestClientWebhookSender(builder.build());
        ReflectionTestUtils.setField(sender, "signatureHeader", "X-Custom-Signature");

        assertEquals(200, sender.post("https://merchant.example/hook", "sig-2", Map.of()));
    }
}
