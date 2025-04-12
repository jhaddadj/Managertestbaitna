package com.example.manager.admin.ui;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.manager.R;
import com.example.manager.databinding.ActivityUserDetailBinding;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import com.example.manager.R;
import com.example.manager.model.User;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import javax.microedition.khronos.opengles.GL;

public class UserDetailActivity extends AppCompatActivity {

    private ActivityUserDetailBinding binding;
    private String userId;
    private User user;
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private DatabaseReference databaseReference;
    private String idUrl;
    private String contractUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityUserDetailBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });





        binding.userIdPhoto.setOnClickListener(v -> {
            // Pass the image URI to ViewImageActivity
            Intent intent = new Intent(this, ViewImageActivity.class);
            intent.putExtra("image_uri", idUrl);
            startActivity(intent);
        });

        binding.userContractPhoto.setOnClickListener(v -> {
            // Pass the image URI to ViewImageActivity
            Intent intent = new Intent(this, ViewImageActivity.class);
            intent.putExtra("image_uri", contractUrl);
            startActivity(intent);
        });
    }

    private Uri getImageUriFromImageView(ImageView imageView) {
        return (Uri) imageView.getTag();
    }
    private void fetchUserDetails(String userId) {
        // Fetch the user from your database using the userId
        // Example: Using Firebase Database
        databaseReference.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                user = snapshot.getValue(User.class);

                if (user != null) {
                    binding.userName.setText("Name: " + user.getName());
                    binding.userEmail.setText("Email: " + user.getEmail());
                    binding.userRole.setText("Role: " + user.getRole());
                    binding.userStatus.setText("Status: " + user.getStatus());
                      idUrl=user.getIdPhoto();
                      contractUrl=user.getContract();
                    // Load images with Picasso or Glide
                    Glide.with(UserDetailActivity.this)
                            .load(user.getIdPhoto()) // URL of the ID photo
                            .into(binding.userIdPhoto);

                    Glide.with(UserDetailActivity.this)
                            .load(user.getContract()) // URL of the contract photo
                            .into(binding.userContractPhoto);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UserDetailActivity.this, "Failed to load user details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onAcceptClick(View view) {
        updateUserStatus("accepted");
    }

    public void onRejectClick(View view) {
        updateUserStatus("rejected");

    }

    private void updateUserStatus(String status) {
        if (user != null) {
            binding.acceptButton.setEnabled(false);
            binding.rejectButton.setEnabled(false);

            user.setStatus(status);
            databaseReference.child(userId).setValue(user)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            binding.acceptButton.setEnabled(true);
                            binding.rejectButton.setEnabled(true);
                            finish();
                        } else {
                            binding.acceptButton.setEnabled(true);
                            binding.rejectButton.setEnabled(true);
                            Toast.makeText(UserDetailActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        userId = getIntent().getStringExtra("id");

        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        // Fetch user details from database based on userId
        fetchUserDetails(userId);
    }
}