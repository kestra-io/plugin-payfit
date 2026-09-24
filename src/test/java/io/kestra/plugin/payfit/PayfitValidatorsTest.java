package io.kestra.plugin.payfit;

import org.junit.jupiter.api.Test;

import io.kestra.plugin.payfit.client.PayfitValidators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayfitValidatorsTest {
    @Test
    void rejectsInvalidPagesDatesStatusesAndIdentityFields() {
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.pageSize(0));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.pageSize(51));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.maxPages(0));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.isoDate("24-12-2026", "startDate"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.accountingPeriod("202613"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.accountingPeriod("199912"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.accountingPeriod("2026-12"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.absenceType("holiday"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.absenceMoment("morning", "startMoment", "beginning-of-day"));
        PayfitValidators.absenceRange("2026-07-01", "beginning-of-day", "2026-07-01", "end-of-day");
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.absenceRange("2026-07-02", "end-of-day", "2026-07-02", "beginning-of-day"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.absenceStatus("remote"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.absenceStatus("all,approved"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.gender("other"));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.children(21));
        assertThrows(IllegalArgumentException.class, () -> PayfitValidators.email("ada", "personalEmail"));

        assertEquals("approved,pending_approval", PayfitValidators.absenceStatus(" approved, pending_approval "));
        assertEquals("202612", PayfitValidators.accountingPeriod("202612"));
        assertEquals("MALE", PayfitValidators.gender("male"));
    }
}
