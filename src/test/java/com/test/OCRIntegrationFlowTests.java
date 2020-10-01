package com.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.test.context.MockIntegrationContext;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.integration.test.mock.MockIntegration.mockMessageHandler;

@RunWith(SpringRunner.class)
@ContextConfiguration(
        initializers = ConfigFileApplicationContextInitializer.class,
        classes = {OCRIntegrationFlow.class, Advices.class, OCRIntegrationFlowTests.Config.class})
@SpringIntegrationTest
@DirtiesContext
public class OCRIntegrationFlowTests {

    @Autowired
    private MockIntegrationContext mockIntegrationContext;

    @Autowired
    private PollableChannel testChannel;

    @Autowired
    private PublishSubscribeChannel errorChannel;

    @Autowired
    private MessageChannel fromTest;

    @Before
    public void prepare() throws IOException {
    }

    @After
    public void tearDown() throws IOException {
    }

    @Test
    public void testOCRFlowFailedOCREndpoint() throws InterruptedException {
        // given
        String documentId = "abc";

        Map<String, Object> headers = new HashMap<>();
        headers.put("DOCUMENT_ID", documentId);

        MessageHandler mockFilenetHandler = mockMessageHandler().handleNextAndReply(m -> "Example file".getBytes());
        this.mockIntegrationContext.substituteMessageHandlerFor("endpoint1", mockFilenetHandler);
        MessageHandler mockCognitiveAnalyze = mockMessageHandler().handleNextAndReply(m -> {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        });
        this.mockIntegrationContext.substituteMessageHandlerFor("cognitiveServicesReadEndpoint",
                mockCognitiveAnalyze);

        // when
        fromTest.send(new GenericMessage<>("", headers));

        // then
        final AtomicReference<Message<?>> messageRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        assert errorChannel.subscribe(m -> {
            messageRef.set(m);
            latch.countDown();
        });

        assert true == latch.await(3, TimeUnit.SECONDS);
        assert ((ErrorMessage) messageRef.get()).getPayload()
                .getMessage()
                .contains("JSON reader was expecting a value but found 'nojson'");

    }

    @Configuration
    @EnableIntegration
    public static class Config {

        @Bean
        public PollableChannel testChannel() {
            return new QueueChannel();
        }

    }
}
