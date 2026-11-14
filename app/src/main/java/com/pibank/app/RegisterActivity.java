package com.pibank.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText etName, etEmail, etPassword, etPassword2;
    private Button btnRegister;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        auth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPassword2 = findViewById(R.id.etPassword2);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);

        btnRegister.setOnClickListener(v -> register());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void register() {
        final String name = etName.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String password2 = etPassword2.getText().toString();

        if (name.isEmpty() || name.length() > 40) {
            toast("Введите имя (до 40 символов)");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Введите корректную почту");
            return;
        }
        if (password.length() < 6) {
            toast("Пароль должен быть не короче 6 символов");
            return;
        }
        if (!password.equals(password2)) {
            toast("Пароли не совпадают");
            return;
        }
        if (!NetUtil.isOnline(this)) {
            toast("Нет подключения к интернету");
            return;
        }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                toast(AuthErrors.message(task.getException()));
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                setLoading(false);
                toast("Не удалось создать аккаунт");
                return;
            }
            Map<String, Object> profile = new HashMap<>();
            profile.put("name", name);
            profile.put("email", email);
            profile.put("createdAt", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance().getReference("users").child(user.getUid())
                    .setValue(profile).addOnCompleteListener(t -> {
                        setLoading(false);
                        if (!t.isSuccessful()) toast("Аккаунт создан, но профиль сохранить не удалось");
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    });
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
