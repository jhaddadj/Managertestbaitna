package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.ui.AdminRegistrationActivity;
import com.example.manager.databinding.ActivityConsentBinding;
import com.example.manager.lecturar.ui.LecturarRegistrationActivity;
import com.example.manager.stduent.ui.StudentRegistrationActivity;

/**
 * ConsentActivity displays the terms and conditions that users must accept before registration.
 * If users accept, they proceed to the appropriate registration activity based on role.
 * If users decline, they are returned to the login screen.
 */
public class ConsentActivity extends AppCompatActivity {

    private ActivityConsentBinding binding;
    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityConsentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Get the role type from the intent
        role = getIntent().getStringExtra("role");
        
        // Set up click listeners for accept and decline buttons
        binding.acceptButton.setOnClickListener(v -> acceptTerms());
        binding.declineButton.setOnClickListener(v -> declineTerms());
    }
    
    /**
     * Handles user acceptance of terms and conditions.
     * Redirects to appropriate registration activity based on user role.
     */
    private void acceptTerms() {
        Intent intent = null;
        
        // Determine which registration activity to open based on role
        if ("1".equals(role)) {
            // Admin registration
            intent = new Intent(ConsentActivity.this, AdminRegistrationActivity.class);
        } else if ("2".equals(role)) {
            // Lecturer registration
            intent = new Intent(ConsentActivity.this, LecturarRegistrationActivity.class);
        } else if ("3".equals(role)) {
            // Student registration
            intent = new Intent(ConsentActivity.this, StudentRegistrationActivity.class);
        }
        
        // Pass the role to the registration activity
        if (intent != null) {
            intent.putExtra("role", role);
            startActivity(intent);
        }
        
        // Finish this activity so user can't go back to consent screen
        finish();
    }
    
    /**
     * Handles user decline of terms and conditions.
     * Returns user to the login activity.
     */
    private void declineTerms() {
        // Go back to login screen
        Intent intent = new Intent(ConsentActivity.this, LoginActivity.class);
        intent.putExtra("role", role);
        startActivity(intent);
        
        // Finish this activity
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Consider a back press as declining terms
        declineTerms();
    }
}
