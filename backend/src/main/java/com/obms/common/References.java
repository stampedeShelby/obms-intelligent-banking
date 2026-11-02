package com.obms.common;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

/**
 * Generators for the business identifiers customers see.
 *
 * <p>{@link SecureRandom} is used rather than {@code Math.random()} because
 * account numbers and transaction references are quoted in support calls and
 * should not be guessable from one another.</p>
 */
public final class References {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private References() {
        // utility class
    }

    /**
     * @return a 14-digit account number, e.g. {@code 10024857391026}
     */
    public static String accountNumber() {
        StringBuilder sb = new StringBuilder("100");
        for (int i = 0; i < 11; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * @return a customer number, e.g. {@code CUS7F3K9Q}
     */
    public static String customerNumber() {
        return "CUS" + token(6);
    }

    /**
     * @return a transaction reference, e.g. {@code TXN20261102K4M9P1}
     */
    public static String transactionReference() {
        return "TXN" + BankTime.today().format(DATE_PART) + token(6);
    }

    /**
     * @return a reference shared by both legs of one transfer
     */
    public static String transferGroupReference() {
        return "TRF" + BankTime.today().format(DATE_PART) + token(6);
    }

    /**
     * @return a bill payment reference, e.g. {@code BIL20261102X7Y2Z9}
     */
    public static String billPaymentReference() {
        return "BIL" + BankTime.today().format(DATE_PART) + token(6);
    }

    private static String token(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
