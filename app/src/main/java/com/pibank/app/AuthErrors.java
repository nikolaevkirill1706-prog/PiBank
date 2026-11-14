package com.pibank.app;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public final class AuthErrors {
    private AuthErrors() {}

    public static String message(Exception e) {
        if (e instanceof FirebaseNetworkException) return "Нет подключения к интернету";
        if (e instanceof FirebaseTooManyRequestsException) return "Слишком много попыток. Попробуйте позже";
        if (e instanceof FirebaseAuthWeakPasswordException) return "Слишком простой пароль (минимум 6 символов)";
        if (e instanceof FirebaseAuthUserCollisionException) return "Эта почта уже зарегистрирована";
        if (e instanceof FirebaseAuthInvalidUserException) return "Пользователь не найден";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "Неверная почта или пароль";
        return "Не удалось выполнить операцию. Попробуйте ещё раз";
    }
}
