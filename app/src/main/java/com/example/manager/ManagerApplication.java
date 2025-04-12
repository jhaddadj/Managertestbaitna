package com.example.manager;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

public class ManagerApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Enable offline capabilities
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
