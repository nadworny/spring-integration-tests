package com.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.stereotype.Component;

@Component
public class OCRIntegrationFlow {

    @Autowired
    private Advices advices;

    @Bean
    public IntegrationFlow ocrDocument() {
        return IntegrationFlows.from("fromTest")
                .handle(Http.outboundGateway("http://testit.com")
                        .httpMethod(HttpMethod.GET)
                        .expectedResponseType(byte[].class), c -> c.advice(this.advices.retryAdvice())
                        .id("endpoint1"))
                .wireTap(sf -> sf.enrichHeaders(h -> h.header("ocp-apim-subscription-key", "computerVisionApiKey"))
                        .handle(Http.outboundGateway("https://northeurope.api.cognitive.microsoft.com/vision/v3" +
                                        ".0/read/analyze")
                                        .mappedRequestHeaders("Ocp-Apim-Subscription-Key")
                                        .mappedResponseHeaders("Operation-Location"),
                                c -> c.advice(this.advices.retryAdvice())
                                        .id("cognitiveServicesReadEndpoint"))
                        .transform(p -> "")
                        .handle(Http.outboundGateway(h -> h.getHeaders().get("Operation-Location"))
                                .mappedRequestHeaders("Ocp-Apim-Subscription-Key")
                                .httpMethod(HttpMethod.GET)
                                .expectedResponseType(String.class), this.advices.ocrSpec())
                        .log(LoggingHandler.Level.INFO, m -> "OCR processed")
                )
                .log(LoggingHandler.Level.INFO, m -> "Doc processed")
                .get();
    }
}
