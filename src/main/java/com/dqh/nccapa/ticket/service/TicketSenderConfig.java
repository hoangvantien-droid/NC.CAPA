package com.dqh.nccapa.ticket.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình TicketSender HTTP mặc định. Tên class KHÁC tên bean method (tránh trùng định nghĩa bean).
 * Test cấp thêm StubTicketSender @Primary để override khi inject.
 */
@Configuration
public class TicketSenderConfig {

    @Bean
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
