package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.databinding.ActivityAdminRegistrationBinding;
import com.example.manager.model.User;
import com.example.manager.stduent.ui.StudentRegistrationActivity;
import com.example.manager.ui.LoginActivity;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AdminRegistrationActivity extends AppCompatActivity {
    private ActivityAdminRegistrationBinding binding;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private String roles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAdminRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        roles = getIntent().getStringExtra("role");

        binding.registerButton.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String name = binding.nameEditText.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String code = binding.codeEditText.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!code.equalsIgnoreCase("1234")) {
            Toast.makeText(this, "Admin code is incorrect", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.registerButton.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();
                        User user = new User(userId,name, email,"", "admin", "", "", "accepted");

                        databaseReference.child(userId).setValue(user)
                                .addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful()) {

                                        Toast.makeText(this, "Account Create Successfully", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(AdminRegistrationActivity.this, LoginActivity.class);
                                        intent.putExtra("role", "1");

                                        startActivity(intent);
                                        finish();
                                    } else {
                                        binding.progressBar.setVisibility(View.INVISIBLE);
                                        binding.registerButton.setEnabled(true);

                                        Toast.makeText(AdminRegistrationActivity.this, "Failed to Save Data", Toast.LENGTH_SHORT).
                                        show();
                                    }
                                });
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.registerButton.setEnabled(true);
                        Toast.makeText(this, "Failed to Create Account", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(AdminRegistrationActivity.this, SelectActivity.class);
        startActivity(intent);
        finish();
    }
}