package com.pibank.app;

import java.math.BigDecimal;
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

    /** "1500", "1500,50" -> копейки. Бросает исключение, если сумма некорректна. */
    public static long parse(String text) {
        String s = text.trim().replace(',', '.').replace(" ", "");
        BigDecimal b = new BigDecimal(s);
        if (b.signum() <= 0 || b.stripTrailingZeros().scale() > 2) throw new NumberFormatException();
        return b.movePointRight(2).longValueExact();
    }
}
