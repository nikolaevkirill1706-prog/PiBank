package com.pibank.app;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

public class PiBankApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Данные сохраняются на устройстве и доступны после перезапуска
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
        }
    }
}
