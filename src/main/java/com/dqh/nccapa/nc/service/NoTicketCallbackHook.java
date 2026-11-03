package com.dqh.nccapa.nc.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op mặc định khi chưa có module ticket. */
@Configuration
public class NoTicketCallbackHook {
    @Bean
    @ConditionalOnMissingBean(TicketCallbackHook.class)
    public TicketCallbackHook noOpTicketCallbackHook() {
        return new TicketCallbackHook() {
            public void mocSom(com.dqh.nccapa.nc.entity.Nc nc) { }
            public void mocDong(com.dqh.nccapa.nc.entity.Nc nc, String ketLuan) { }
        };
    }
}
