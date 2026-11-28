package com.smartportx.container.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerNumbersTest {

    @Test
    void knownValidExampleAccepts() {
        assertTrue(ContainerNumbers.isValid("CSQU3054383"));
    }

    @Test
    void knownInvalidExampleRejects() {
        assertFalse(ContainerNumbers.isValid("MSKU1234567"));
    }

    @Test
    void checkDigitOfKnownExampleIsThree() {
        assertEquals(3, ContainerNumbers.checkDigit("CSQU305438"));
    }

    @Test
    void generatedNumbersRoundTrip() {
        AtomicInteger serial = new AtomicInteger(100000);
        for (int i = 0; i < 50; i++) {
            String prefix = "TSTU" + serial.getAndIncrement();
            String number = prefix + ContainerNumbers.checkDigit(prefix);
            assertEquals(11, number.length());
            assertTrue(ContainerNumbers.isValid(number));
        }
    }

    @Test
    void flippedCheckDigitRejects() {
        String valid = "CSQU3054383";
        char corrupted = valid.charAt(10) == '9' ? '0' : (char) (valid.charAt(10) + 1);
        assertFalse(ContainerNumbers.isValid(valid.substring(0, 10) + corrupted));
    }

    @Test
    void wrongLengthRejects() {
        assertFalse(ContainerNumbers.isValid("CSQU305438"));
        assertFalse(ContainerNumbers.isValid("CSQU30543833"));
    }

    @Test
    void nonLetterOwnerRejects() {
        assertFalse(ContainerNumbers.isValid("1SQU3054383"));
    }

    @Test
    void nonDigitSerialRejects() {
        assertFalse(ContainerNumbers.isValid("CSQU3A54383"));
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertEquals("CSQU3054383", ContainerNumbers.normalize("  csqu3054383  "));
    }

    @Test
    void generatedNumberIsLegalContraction() {
        String prefix = "CICU" + "123456";
        String number = prefix + ContainerNumbers.checkDigit(prefix);
        assertTrue(ContainerNumbers.isValid(number));
    }
}