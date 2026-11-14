package com.pibank.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final long LOAD_TIMEOUT_MS = 12000;

    private FirebaseAuth auth;
    private DatabaseReference root, cardsRef;
    private ValueEventListener cardsListener;
    private String uid, email, userName = "";

    private TextView tvGreeting, tvTotal, tvEmptyCards, tvError;
    private View progress, errorBox;
    private CardAdapter adapter;
    private final List<Card> cards = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loaded = false;
    private boolean creating = false;

    private final Runnable timeout = () -> {
        if (!loaded) showError("Не удалось загрузить данные. Проверьте подключение к интернету.");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            goLogin();
            return;
        }
        uid = user.getUid();
        email = user.getEmail() == null ? "" : user.getEmail();
        root = FirebaseDatabase.getInstance().getReference();
        cardsRef = root.child("cards").child(uid);

        setContentView(R.layout.activity_main);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvTotal = findViewById(R.id.tvTotal);
        tvEmptyCards = findViewById(R.id.tvEmptyCards);
        tvError = findViewById(R.id.tvError);
        progress = findViewById(R.id.progress);
        errorBox = findViewById(R.id.errorBox);

        RecyclerView rv = findViewById(R.id.rvCards);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new CardAdapter(cards);
        rv.setAdapter(adapter);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            auth.signOut();
            goLogin();
        });
        findViewById(R.id.btnNewCard).setOnClickListener(v -> showCreateDialog());
        findViewById(R.id.btnRetry).setOnClickListener(v -> load());

        updateGreeting();
        updateUi();
        load();
    }

    private void load() {
        loaded = false;
        errorBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        if (!NetUtil.isOnline(this)) {
            Toast.makeText(this, "Нет подключения к интернету. Показаны сохранённые данные.", Toast.LENGTH_LONG).show();
        }

        root.child("users").child(uid).child("name").get().addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null && t.getResult().getValue() != null) {
                userName = String.valueOf(t.getResult().getValue());
                updateGreeting();
            }
        });

        if (cardsListener != null) cardsRef.removeEventListener(cardsListener);
        cardsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                cards.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Card c = Card.from(child);
                    if (c != null) cards.add(c);
                }
                Collections.sort(cards, (a, b) -> Long.compare(a.createdAt, b.createdAt));
                loaded = true;
                handler.removeCallbacks(timeout);
                progress.setVisibility(View.GONE);
                errorBox.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                updateUi();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                handler.removeCallbacks(timeout);
                showError("Не удалось загрузить данные: " + error.getMessage());
            }
        };
        cardsRef.addValueEventListener(cardsListener);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS);
    }

    private void showError(String text) {
        progress.setVisibility(View.GONE);
        tvError.setText(text);
        errorBox.setVisibility(View.VISIBLE);
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = h < 6 ? "Доброй ночи" : h < 12 ? "Доброе утро" : h < 18 ? "Добрый день" : "Добрый вечер";
        String name = userName.isEmpty() ? "" : ", " + userName;
        tvGreeting.setText(hello + name);
    }

    private void updateUi() {
        long total = 0;
        for (Card c : cards) total += c.balance;
        tvTotal.setText(Money.format(total));
        tvEmptyCards.setVisibility(cards.isEmpty() && loaded ? View.VISIBLE : View.GONE);
    }

    private void showCreateDialog() {
        if (!loaded) {
            Toast.makeText(this, "Данные ещё загружаются", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cards.size() >= 3) {
            Toast.makeText(this, "Можно создать максимум три карты", Toast.LENGTH_LONG).show();
            return;
        }
        if (creating) return;
        final String[] labels = {"Дебетовая", "Кредитная", "Детская"};
        final String[] types = {"debit", "credit", "child"};
        new AlertDialog.Builder(this)
                .setTitle("Новая карта vida")
                .setItems(labels, (d, which) -> createCard(types[which], 0))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createCard(String type, int attempt) {
        if (!NetUtil.isOnline(this)) {
            Toast.makeText(this, "Нет подключения к интернету", Toast.LENGTH_LONG).show();
            return;
        }
        creating = true;
        String number = CardFactory.number();
        String cardId = cardsRef.push().getKey();
        if (cardId == null) {
            creating = false;
            return;
        }
        String base = userName.isEmpty() ? email.split("@")[0] : userName;
        String holder = base.toUpperCase();
        if (holder.length() > 26) holder = holder.substring(0, 26);

        Map<String, Object> card = new HashMap<>();
        card.put("number", number);
        card.put("type", type);
        card.put("holder", holder);
        card.put("expiry", CardFactory.expiry());
        card.put("cvv", CardFactory.cvv());
        card.put("balance", 0L);
        card.put("status", "active");
        card.put("createdAt", ServerValue.TIMESTAMP);

        // Один атомарный запрос: карта + запись в индексе номеров (гарантирует уникальность номера)
        Map<String, Object> updates = new HashMap<>();
        updates.put("cards/" + uid + "/" + cardId, card);
        updates.put("cardIndex/" + number, uid);

        root.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                creating = false;
                Toast.makeText(this, "Карта создана", Toast.LENGTH_SHORT).show();
            } else if (attempt < 3) {
                createCard(type, attempt + 1); // номер мог совпасть — пробуем новый
            } else {
                creating = false;
                Toast.makeText(this, "Не удалось создать карту. Попробуйте ещё раз.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timeout);
        if (cardsRef != null && cardsListener != null) cardsRef.removeEventListener(cardsListener);
    }
}
