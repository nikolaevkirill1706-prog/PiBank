package com.pibank.app;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Locale;

public final class CardFactory {
    private static final SecureRandom R = new SecureRandom();

    private CardFactory() {}

    /** 16 цифр, последняя — контрольная (алгоритм Луна). */
    public static String number() {
        StringBuilder sb = new StringBuilder("5400");
        for (int i = 0; i < 11; i++) sb.append(R.nextInt(10));
        sb.append(luhn(sb.toString()));
        return sb.toString();
    }

    private static int luhn(String payload) {
        int sum = 0;
        boolean dbl = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - sum % 10) % 10;
    }

    public static String expiry() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 4);
        return String.format(Locale.US, "%02d/%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100);
    }

    public static String cvv() {
        return String.format(Locale.US, "%03d", R.nextInt(1000));
    }
}
