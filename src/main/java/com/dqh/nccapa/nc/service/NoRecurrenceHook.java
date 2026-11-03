package com.dqh.nccapa.nc.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op mặc định khi chưa có module CAPA (nhường chỗ cho RecurrenceDetector). */
@Configuration
public class NoRecurrenceHook {
    @Bean
    @ConditionalOnMissingBean(RecurrenceHook.class)
    public RecurrenceHook noOpRecurrenceHook() {
        return ncMoi -> false;
    }
}
