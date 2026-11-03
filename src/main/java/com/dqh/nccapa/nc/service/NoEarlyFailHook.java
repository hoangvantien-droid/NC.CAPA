package com.dqh.nccapa.nc.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op mặc định khi chưa có module CAPA (nhường chỗ cho EarlyFailDetector). */
@Configuration
public class NoEarlyFailHook {
    @Bean
    @ConditionalOnMissingBean(EarlyFailHook.class)
    public EarlyFailHook noOpEarlyFailHook() {
        return ncMoi -> false;
    }
}
