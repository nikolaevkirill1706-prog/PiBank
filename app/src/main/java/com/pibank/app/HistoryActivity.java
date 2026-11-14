package com.pibank.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private Query query;
    private ValueEventListener listener;
    private final List<Op> ops = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_history);
        final View progress = findViewById(R.id.progress);
        final TextView empty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final OpAdapter adapter = new OpAdapter(ops);
        rv.setAdapter(adapter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        query = FirebaseDatabase.getInstance().getReference("operations").child(uid)
                .orderByChild("createdAt").limitToLast(100);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                ops.clear();
                for (DataSnapshot c : snapshot.getChildren()) {
                    Op o = Op.from(c);
                    if (o != null) ops.add(o);
                }
                Collections.sort(ops, (a, b) -> Long.compare(b.createdAt, a.createdAt));
                adapter.notifyDataSetChanged();
                progress.setVisibility(View.GONE);
                empty.setVisibility(ops.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(HistoryActivity.this, "Не удалось загрузить историю", Toast.LENGTH_LONG).show();
            }
        };
        query.addValueEventListener(listener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (query != null && listener != null) query.removeEventListener(listener);
    }
}
