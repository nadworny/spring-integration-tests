package com.test;

import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.GenericEndpointSpec;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.http.outbound.HttpRequestExecutingMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Advices and specs
 */
@Configuration
public class Advices {

    /**
     * Retry advice
     *
     * @return the request handler retry advice
     */
    @Bean
    public RequestHandlerRetryAdvice retryAdvice() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(3000l);
        exponentialBackOffPolicy.setMultiplier(3);
        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        RequestHandlerRetryAdvice requestHandlerRetryAdvice = new RequestHandlerRetryAdvice();
        requestHandlerRetryAdvice.setRetryTemplate(retryTemplate);
        requestHandlerRetryAdvice.setRecoveryCallback(new ErrorMessageSendingRecoverer(recoveryChannel()));
        return requestHandlerRetryAdvice;
    }

    /**
     * Retry advice until the processing of an async request is complete
     *
     * @return the request handler retry advice
     */
    @Bean
    public RequestHandlerRetryAdvice retryUntilRequestCompleteAdvice() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(3000l);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(20);
        retryTemplate.setRetryPolicy(retryPolicy);

        RequestHandlerRetryAdvice requestHandlerRetryAdvice = new RequestHandlerRetryAdvice();
        requestHandlerRetryAdvice.setRetryTemplate(retryTemplate);
        requestHandlerRetryAdvice.setRecoveryCallback(new ErrorMessageSendingRecoverer(recoveryChannel()));
        return requestHandlerRetryAdvice;
    }

    /**
     * Advice for OCR read interface that checks if the results are available
     *
     * @return the advice
     */
    @Bean
    public Advice verifyReplySuccess() {
        return new AbstractRequestHandlerAdvice() {
            @Override
            protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
                Object payload = ((MessageBuilder) callback.execute()).build().getPayload();
                if (((String) payload).contains("analyzeResult"))
                    return payload;
                else
                    throw new RuntimeException("Still analyzing");
            }
        };
    }

    /**
     * Advice that checks if a result of a callback is null. If so, return an empty list.
     * Can be replaced once this is implemented: https://jira.spring.io/browse/INT-3333
     *
     * @return the advice
     */
    @Bean
    public Advice verifyEmptyResult() {
        return new AbstractRequestHandlerAdvice() {
            @Override
            protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
                Object executed = callback.execute();
                if (executed == null)
                    return new ArrayList<>();
                else
                    return executed;
            }
        };
    }

    /**
     * Recovery channel
     *
     * @return the message channel
     */
    @Bean
    public MessageChannel recoveryChannel() {
        return new DirectChannel();
    }


    @Bean
    public Consumer<GenericEndpointSpec<HttpRequestExecutingMessageHandler>> ocrSpec() {
        return spec -> {
            spec.advice(retryUntilRequestCompleteAdvice(), verifyReplySuccess());
            spec.id("ocrEndpoint");
        };
    }
}
