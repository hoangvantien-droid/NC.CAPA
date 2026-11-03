package com.dqh.nccapa.ticket.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Impl HTTP mặc định (nhường chỗ cho stub trong test qua @ConditionalOnMissingBean). */
@Configuration
public class HttpTicketSender {

    @Bean
    @ConditionalOnMissingBean(TicketSender.class)
    public TicketSender httpTicketSender() {
        RestClient client = RestClient.create();
        return (url, payloadJson) -> {
            try {
                client.post().uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payloadJson)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }
}
