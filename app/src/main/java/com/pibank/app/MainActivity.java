package com.pibank.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final long LOAD_TIMEOUT_MS = 12000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private FirebaseAuth auth;
    private FirebaseFunctions functions;
    private DatabaseReference root, cardsRef, loanRef;
    private Query opsQuery;
    private ValueEventListener cardsListener, loanListener, opsListener;
    private String uid, email, userName = "";

    private TextView tvGreeting, tvTotal, tvEmptyCards, tvEmptyOps, tvError, tvLoanTitle, tvLoanInfo;
    private View progress, errorBox, loanPanel;
    private CardAdapter adapter;
    private OpAdapter opAdapter;
    private final List<Card> cards = new ArrayList<>();
    private final List<Op> ops = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loaded = false;
    private boolean busy = false;

    private boolean loanActive = false;
    private long loanPrincipal = 0, loanDueAt = 0;

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
        loanRef = root.child("loans").child(uid);
        opsQuery = root.child("operations").child(uid).orderByChild("createdAt").limitToLast(5);
        functions = FirebaseFunctions.getInstance();

        setContentView(R.layout.activity_main);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvTotal = findViewById(R.id.tvTotal);
        tvEmptyCards = findViewById(R.id.tvEmptyCards);
        tvEmptyOps = findViewById(R.id.tvEmptyOps);
        tvError = findViewById(R.id.tvError);
        tvLoanTitle = findViewById(R.id.tvLoanTitle);
        tvLoanInfo = findViewById(R.id.tvLoanInfo);
        progress = findViewById(R.id.progress);
        errorBox = findViewById(R.id.errorBox);
        loanPanel = findViewById(R.id.loanPanel);

        RecyclerView rv = findViewById(R.id.rvCards);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new CardAdapter(cards);
        rv.setAdapter(adapter);

        RecyclerView rvOps = findViewById(R.id.rvOps);
        rvOps.setLayoutManager(new LinearLayoutManager(this));
        opAdapter = new OpAdapter(ops);
        rvOps.setAdapter(opAdapter);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            auth.signOut();
            goLogin();
        });
        findViewById(R.id.btnNewCard).setOnClickListener(v -> showCreateDialog());
        findViewById(R.id.btnRetry).setOnClickListener(v -> load());
        findViewById(R.id.btnTransfer).setOnClickListener(v -> showTransferDialog());
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btnRepay).setOnClickListener(v -> showRepayDialog());

        updateGreeting();
        updateUi();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderLoan();
    }

    // ---------- загрузка данных ----------

    private void detachListeners() {
        if (cardsListener != null) cardsRef.removeEventListener(cardsListener);
        if (loanListener != null) loanRef.removeEventListener(loanListener);
        if (opsListener != null) opsQuery.removeEventListener(opsListener);
    }

    private void load() {
        loaded = false;
        errorBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        if (!NetUtil.isOnline(this)) {
            toast("Нет подключения к интернету. Показаны сохранённые данные.");
        }

        root.child("users").child(uid).child("name").get().addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null && t.getResult().getValue() != null) {
                userName = String.valueOf(t.getResult().getValue());
                updateGreeting();
            }
        });

        detachListeners();

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

        loanListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                String status = s.child("status").getValue(String.class);
                Long principal = s.child("principal").getValue(Long.class);
                Long dueAt = s.child("dueAt").getValue(Long.class);
                loanActive = principal != null && dueAt != null
                        && ("active".equals(status) || "overdue".equals(status));
                if (loanActive) {
                    loanPrincipal = principal;
                    loanDueAt = dueAt;
                }
                renderLoan();
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        loanRef.addValueEventListener(loanListener);

        opsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                ops.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Op o = Op.from(child);
                    if (o != null) ops.add(o);
                }
                Collections.sort(ops, (a, b) -> Long.compare(b.createdAt, a.createdAt));
                opAdapter.notifyDataSetChanged();
                tvEmptyOps.setVisibility(ops.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        opsQuery.addValueEventListener(opsListener);

        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS);
    }

    private void showError(String text) {
        progress.setVisibility(View.GONE);
        tvError.setText(text);
        errorBox.setVisibility(View.VISIBLE);
    }

    // ---------- отображение ----------

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

    private String fmtDate(long ms) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("ru")).format(new Date(ms));
    }

    private void renderLoan() {
        if (loanPanel == null) return;
        if (!loanActive) {
            loanPanel.setVisibility(View.GONE);
            return;
        }
        loanPanel.setVisibility(View.VISIBLE);
        boolean overdue = System.currentTimeMillis() > loanDueAt;
        if (overdue) {
            tvLoanTitle.setText("Кредит просрочен");
            tvLoanTitle.setTextColor(getResources().getColor(R.color.error, null));
            tvLoanInfo.setText("К оплате: " + Money.format(loanPrincipal * 2) + " (двойная сумма).\n"
                    + "Если не погасить до " + fmtDate(loanDueAt + 3 * DAY_MS)
                    + ", все ваши карты будут удалены.");
        } else {
            tvLoanTitle.setText("Кредит");
            tvLoanTitle.setTextColor(getResources().getColor(R.color.white, null));
            tvLoanInfo.setText("Сумма кредита: " + Money.format(loanPrincipal) + "\n"
                    + "Вернуть до: " + fmtDate(loanDueAt) + "\n"
                    + "После этого срока долг удваивается: " + Money.format(loanPrincipal * 2));
        }
    }

    // ---------- действия ----------

    private String last4(Card c) {
        return c.number.length() >= 4 ? c.number.substring(c.number.length() - 4) : c.number;
    }

    private String[] cardLabels() {
        String[] a = new String[cards.size()];
        for (int i = 0; i < a.length; i++) {
            Card c = cards.get(i);
            a[i] = c.typeTitle() + " ••••" + last4(c) + " · " + Money.format(c.balance);
        }
        return a;
    }

    private void showCreateDialog() {
        if (!loaded) {
            toast("Данные ещё загружаются");
            return;
        }
        if (cards.size() >= 3) {
            toast("Можно создать максимум три карты");
            return;
        }
        final String[] labels = {"Дебетовая", "Кредитная", "Детская"};
        final String[] types = {"debit", "credit", "child"};
        new AlertDialog.Builder(this)
                .setTitle("Новая карта vida")
                .setItems(labels, (d, which) -> {
                    if ("credit".equals(types[which])) {
                        showCreditDialog();
                    } else {
                        Map<String, Object> data = new HashMap<>();
                        data.put("type", types[which]);
                        callFn("createCard", data, "Карта создана");
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showCreditDialog() {
        if (loanActive) {
            toast("У вас уже есть непогашенный кредит");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_credit, null);
        final EditText etAmount = v.findViewById(R.id.etAmount);
        final Spinner sp = v.findViewById(R.id.spTerm);
        final int[] terms = {3, 7, 14, 30};
        String[] labels = {"Срок: 3 дня", "Срок: 7 дней", "Срок: 14 дней", "Срок: 30 дней"};
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Кредитная карта vida")
                .setView(v)
                .setPositiveButton("Оформить", null)
                .setNegativeButton("Отмена", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            long minor;
            try {
                minor = Money.parse(etAmount.getText().toString());
            } catch (Exception e) {
                toast("Введите сумму, например 5000");
                return;
            }
            if (minor < 10000L || minor > 10000000L) {
                toast("Сумма кредита: от 100 до 100 000");
                return;
            }
            dlg.dismiss();
            Map<String, Object> data = new HashMap<>();
            data.put("type", "credit");
            data.put("amount", minor);
            data.put("termDays", terms[sp.getSelectedItemPosition()]);
            callFn("createCard", data, "Кредитная карта создана, деньги зачислены");
        });
    }

    private void showTransferDialog() {
        if (cards.isEmpty()) {
            toast("Сначала создайте карту");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Списать с карты")
                .setItems(cardLabels(), (d, which) -> showTransferForm(cards.get(which)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showTransferForm(final Card from) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_transfer, null);
        final EditText etNumber = v.findViewById(R.id.etNumber);
        final EditText etAmount = v.findViewById(R.id.etAmount);
        ((TextView) v.findViewById(R.id.tvFrom)).setText(
                "С карты ••••" + last4(from) + ", доступно " + Money.format(from.balance));

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Перевод")
                .setView(v)
                .setPositiveButton("Перевести", null)
                .setNegativeButton("Отмена", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String number = etNumber.getText().toString().replaceAll("\\s", "");
            if (!number.matches("\\d{16}")) {
                toast("Введите 16 цифр номера карты");
                return;
            }
            long minor;
            try {
                minor = Money.parse(etAmount.getText().toString());
            } catch (Exception e) {
                toast("Введите сумму, например 250");
                return;
            }
            if (minor > from.balance) {
                toast("Недостаточно средств на карте");
                return;
            }
            dlg.dismiss();
            Map<String, Object> data = new HashMap<>();
            data.put("fromCardId", from.id);
            data.put("toNumber", number);
            data.put("amount", minor);
            callFn("transfer", data, "Перевод выполнен");
        });
    }

    private void showRepayDialog() {
        if (!loanActive) return;
        if (cards.isEmpty()) {
            toast("Нет карты для оплаты");
            return;
        }
        long due = System.currentTimeMillis() > loanDueAt ? loanPrincipal * 2 : loanPrincipal;
        new AlertDialog.Builder(this)
                .setTitle("Оплатить " + Money.format(due) + " с карты")
                .setItems(cardLabels(), (d, which) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("fromCardId", cards.get(which).id);
                    callFn("repayLoan", data, "Кредит погашен");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------- вызов серверных функций ----------

    private void callFn(String name, Map<String, Object> data, final String okMessage) {
        if (busy) return;
        if (!NetUtil.isOnline(this)) {
            toast("Нет подключения к интернету");
            return;
        }
        busy = true;
        progress.setVisibility(View.VISIBLE);
        functions.getHttpsCallable(name).call(data).addOnCompleteListener(task -> {
            busy = false;
            progress.setVisibility(View.GONE);
            if (task.isSuccessful()) toast(okMessage);
            else toast(fnError(task.getException()));
        });
    }

    private String fnError(Exception e) {
        if (e instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException f = (FirebaseFunctionsException) e;
            String msg = f.getMessage();
            if (f.getCode() == FirebaseFunctionsException.Code.NOT_FOUND && "NOT FOUND".equalsIgnoreCase(msg)) {
                return "Серверная часть PiBank ещё не установлена";
            }
            if (msg != null && !msg.isEmpty() && !msg.matches("[A-Z_ ]+")) return msg;
        }
        return "Ошибка сервера. Попробуйте ещё раз позже";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
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
        if (cardsRef != null) detachListeners();
    }
}
