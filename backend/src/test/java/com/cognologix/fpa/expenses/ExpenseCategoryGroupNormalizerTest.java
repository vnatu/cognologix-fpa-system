package com.cognologix.fpa.expenses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCategoryGroupNormalizerTest {

    @Test
    void toTitleCase_trimsWordsAndCapitalizesEach() {
        assertThat(ExpenseService.toTitleCase("facilities")).isEqualTo("Facilities");
        assertThat(ExpenseService.toTitleCase("PEOPLE AND WELFARE")).isEqualTo("People And Welfare");
        assertThat(ExpenseService.toTitleCase("travel and transport")).isEqualTo("Travel And Transport");
        assertThat(ExpenseService.toTitleCase("  delivery costs  ".trim())).isEqualTo("Delivery Costs");
    }
}
