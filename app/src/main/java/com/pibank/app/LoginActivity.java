package com.pibank.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();

        // Авторизация сохраняется: если пользователь уже вошёл, сразу открываем главный экран
        if (auth.getCurrentUser() != null) {
            startMain();
            return;
        }

        setContentView(R.layout.activity_login);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.progress);

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.btnForgot).setOnClickListener(v -> resetPassword());
        findViewById(R.id.btnRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            toast("Введите почту и пароль");
            return;
        }
        if (!NetUtil.isOnline(this)) {
            toast("Нет подключения к интернету");
            return;
        }
        setLoading(true);
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) startMain();
            else toast(AuthErrors.message(task.getException()));
        });
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            toast("Введите почту, на которую придёт письмо");
            return;
        }
        if (!NetUtil.isOnline(this)) {
            toast("Нет подключения к интернету");
            return;
        }
        setLoading(true);
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) toast("Письмо для восстановления пароля отправлено");
            else toast(AuthErrors.message(task.getException()));
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
