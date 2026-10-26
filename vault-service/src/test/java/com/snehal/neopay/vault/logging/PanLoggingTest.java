package com.snehal.neopay.vault.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.snehal.neopay.vault.dto.request.TokenizeRequest;
import com.snehal.neopay.vault.entity.CardToken;
import com.snehal.neopay.vault.entity.VaultCard;
import com.snehal.neopay.vault.processor.CardPaymentProcessor;
import com.snehal.neopay.vault.repository.CardTokenRepository;
import com.snehal.neopay.vault.repository.VaultCardRepository;
import com.snehal.neopay.vault.service.impl.VaultServiceImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PanLoggingTest {

    private static final String PAN = "4111111111111111";

    @Test
    void panNeverAppearsInLogOutput() {
        CardTokenRepository cardTokenRepository = mock(CardTokenRepository.class);
        VaultCardRepository vaultCardRepository = mock(VaultCardRepository.class);
        BytesEncryptor dekEncrypter = mock(BytesEncryptor.class);
        when(dekEncrypter.encrypt(any())).thenReturn(new byte[]{1, 2, 3});
        when(vaultCardRepository.save(any(VaultCard.class))).thenAnswer(i -> i.getArgument(0));
        when(cardTokenRepository.save(any(CardToken.class))).thenAnswer(i -> i.getArgument(0));

        VaultServiceImpl service = new VaultServiceImpl(cardTokenRepository, vaultCardRepository,
                dekEncrypter, mock(CardPaymentProcessor.class));

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        try {
            service.tokenize(new TokenizeRequest(PAN, "123", 12, 2030, null, "Test User"),
                    UUID.randomUUID());
        } finally {
            root.detachAppender(appender);
        }

        boolean leaked = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(PAN));

        assertFalse(leaked, "PAN must never be written to logs");
    }
}
