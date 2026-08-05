package com.b201.ecovillage.property.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PropertySettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(PropertySettlementScheduler.class);
    private final PropertyService properties;

    public PropertySettlementScheduler(PropertyService properties) {
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ecovillage.property.settlement-delay-ms:60000}")
    public void settleDuePayments() {
        try {
            properties.collectDueRentAndSubscriptions();
        } catch (RuntimeException exception) {
            log.error("Failed to settle due property payments", exception);
        }
    }
}
