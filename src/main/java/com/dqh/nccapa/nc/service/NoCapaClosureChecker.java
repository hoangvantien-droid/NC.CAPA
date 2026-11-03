package com.dqh.nccapa.nc.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * Impl mặc định cho lát 2 (chưa có module CAPA): không CAPA nào chặn đóng NC.
 * Khi lát CAPA thêm impl thật (một @Service CapaClosureChecker), bean này tự nhường chỗ.
 */
@Configuration
public class NoCapaClosureChecker {

    @Bean
    @ConditionalOnMissingBean(CapaClosureChecker.class)
    public CapaClosureChecker noOpCapaClosureChecker() {
        return ncSoHieu -> null;
    }
}
