package com.b201.bibim.domain.fund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FundCostOptionEntityTest {

    @Test
    void updatesAClassFundCostOption() {
        FundCostOptionEntity option = FundCostOptionEntity.create(
                UUID.randomUUID(), "간식 준비", 100, 1
        );

        option.update(" 학급 행사 ", 250, false);

        assertEquals("학급 행사", option.getName());
        assertEquals(250, option.getAmount());
        assertFalse(option.isEnabled());
    }
}
