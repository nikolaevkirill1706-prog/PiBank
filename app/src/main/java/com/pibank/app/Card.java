package com.pibank.app;

import com.google.firebase.database.DataSnapshot;

public class Card {
    public String id, number, type, holder, expiry, cvv, status;
    public long balance, createdAt;

    public static Card from(DataSnapshot s) {
        try {
            Card c = new Card();
            c.id = s.getKey();
            c.number = s.child("number").getValue(String.class);
            c.type = s.child("type").getValue(String.class);
            c.holder = s.child("holder").getValue(String.class);
            c.expiry = s.child("expiry").getValue(String.class);
            c.cvv = s.child("cvv").getValue(String.class);
            c.status = s.child("status").getValue(String.class);
            Long b = s.child("balance").getValue(Long.class);
            Long t = s.child("createdAt").getValue(Long.class);
            c.balance = b == null ? 0 : b;
            c.createdAt = t == null ? 0 : t;
            if (c.id == null || c.number == null) return null;
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public String typeTitle() {
        String t = "debit".equals(type) ? "Дебетовая" : "credit".equals(type) ? "Кредитная" : "Детская";
        if (status != null && !"active".equals(status)) t += " · заблокирована";
        return t;
    }

    public String spacedNumber() {
        return number.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
