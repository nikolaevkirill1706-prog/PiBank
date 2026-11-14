package com.pibank.app;

import java.util.Locale;

public final class Money {
    public static final String CURRENCY = "₽";

    private Money() {}

    /** Суммы хранятся в базе в минимальных единицах (копейках). */
    public static String format(long minor) {
        long abs = Math.abs(minor);
        String whole = String.format(Locale.forLanguageTag("ru-RU"), "%,d", abs / 100);
        String frac = String.format(Locale.US, "%02d", abs % 100);
        return (minor < 0 ? "-" : "") + whole + "," + frac + " " + CURRENCY;
    }
}
