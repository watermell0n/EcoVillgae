package com.b201.ecovillage.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoanCreditPolicyTest {

    @Test
    void appliesScoreBandsToTheTeacherMaximum() {
        assertThat(LoanCreditPolicy.availableLimit(300, 750)).isEqualTo(300);
        assertThat(LoanCreditPolicy.availableLimit(300, 600)).isEqualTo(240);
        assertThat(LoanCreditPolicy.availableLimit(300, 450)).isEqualTo(180);
        assertThat(LoanCreditPolicy.availableLimit(300, 449)).isZero();
    }

    @Test
    void mapsScoresToStudentCreditLevels() {
        assertThat(LoanCreditPolicy.level(750)).isEqualTo("GOOD");
        assertThat(LoanCreditPolicy.level(600)).isEqualTo("NORMAL");
        assertThat(LoanCreditPolicy.level(450)).isEqualTo("CAUTION");
        assertThat(LoanCreditPolicy.level(449)).isEqualTo("RESTRICTED");
    }
}
