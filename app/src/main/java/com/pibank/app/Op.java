package com.pibank.app;

import com.google.firebase.database.DataSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Op {
    public String id, type, info;
    public long amount, createdAt;

    public static Op from(DataSnapshot s) {
        try {
            Op o = new Op();
            o.id = s.getKey();
            o.type = s.child("type").getValue(String.class);
            o.info = s.child("info").getValue(String.class);
            Long a = s.child("amount").getValue(Long.class);
            Long t = s.child("createdAt").getValue(Long.class);
            o.amount = a == null ? 0 : a;
            o.createdAt = t == null ? 0 : t;
            if (o.id == null || o.type == null) return null;
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    public String title() {
        switch (type) {
            case "transfer_out": return "Перевод";
            case "transfer_in": return "Поступление";
            case "loan_issued": return "Кредит получен";
            case "loan_repaid": return "Кредит погашен";
            case "cards_deleted": return "Карты удалены";
            default: return "Операция";
        }
    }

    /** +1 — деньги пришли, -1 — ушли, 0 — без суммы. */
    public int sign() {
        if (amount <= 0) return 0;
        return ("transfer_in".equals(type) || "loan_issued".equals(type)) ? 1 : -1;
    }

    public String amountText() {
        int s = sign();
        if (s == 0) return "";
        return (s > 0 ? "+" : "−") + Money.format(amount);
    }

    public String dateText() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("ru")).format(new Date(createdAt));
    }
}
