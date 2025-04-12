package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.databinding.ActivitySelectBinding;
import com.google.firebase.auth.FirebaseAuth;

/**
 * SelectActivity serves as the entry point for user role selection in the FinalManager app.
 * This activity presents options for users to select their role (administrator, lecturer, or student)
 * before proceeding to the login or registration screen specific to that role.
 * 
 * The role selection determines which specific interfaces and functionalities will be available
 * to the user throughout the application.
 */
public class SelectActivity extends AppCompatActivity {
    private ActivitySelectBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        binding = ActivitySelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set up click listeners for each role selection button
        binding.adminButton.setOnClickListener(v -> {
            // Navigate to login with role=1 (Admin)
            navigateToLogin("1");
        });

        binding.lecturarButton.setOnClickListener(v -> {
            // Navigate to login with role=2 (Lecturer)
            navigateToLogin("2");
        });

        binding.studentButton.setOnClickListener(v -> {
            // Navigate to login with role=3 (Student)
            navigateToLogin("3");
        });
    }

    /**
     * Navigates to the LoginActivity with the appropriate role parameter.
     * This parameter will be used by the LoginActivity to determine which 
     * registration path to offer and which role-specific validation to perform.
     * 
     * @param role String representing the user role ("1"=admin, "2"=lecturer, "3"=student)
     */
    private void navigateToLogin(String role) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("role", role);
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (FirebaseAuth.getInstance().getCurrentUser()!=null){
           finish();
        }
    }
}