package com.example.manager.stduent.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import com.example.manager.R;
import com.example.manager.databinding.FragmentProfile2Binding;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProfileFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProfileFragment extends Fragment {
private FragmentProfile2Binding binding;


    private FirebaseUser user;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private Uri idImageUri;
    private Uri contractImageUri;
    private AppCompatImageView selectedImageView;
    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public ProfileFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the ProfileFragment with specified parameters
     */
    public static ProfileFragment newInstance(String param1, String param2) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        user = FirebaseAuth.getInstance().getCurrentUser();

        databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());;
        storageReference = FirebaseStorage.getInstance().getReference("uploads");

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentProfile2Binding.inflate(inflater, container, false);
        if (user != null) {
            binding.nameTextView.setText(user.getDisplayName());
            binding.emailTextView.setText(user.getEmail());
            loadProfileData();
        }
        // Set up image upload buttons

        binding.selectIdImageButton.setOnClickListener(v -> pickImage(idImagePickerLauncher));
        binding.selectContractImageButton.setOnClickListener(v -> pickImage(contractImagePickerLauncher));

        binding.updateButton.setOnClickListener(v -> updateProfileData());



        binding.logoutButton.setOnClickListener(v -> logout());
        return binding.getRoot();

    }
    private final ActivityResultLauncher<String> idImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    idImageUri = uri;
                    binding.idImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String> contractImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    contractImageUri = uri;
                    binding.contractImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!result.containsValue(false)) {

                    Toast.makeText(getActivity(), "Permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "Please grant permissions to continue", Toast.LENGTH_SHORT).show();
                }
            });

    private void pickImage(ActivityResultLauncher<String> launcher) {
        permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        launcher.launch("image/*");
    }


    private void loadProfileData() {
        // Load ID photo
        databaseReference.child("idPhoto").get().addOnSuccessListener(snapshot -> {
            String idImageUrl = snapshot.getValue(String.class);
            if (idImageUrl != null) {
                Glide.with(this).load(idImageUrl).into(binding.idImageView);
            }
        });

        // Load contract photo
        databaseReference.child("contract").get().addOnSuccessListener(snapshot -> {
            String contractImageUrl = snapshot.getValue(String.class);
            if (contractImageUrl != null) {
                Glide.with(this).load(contractImageUrl).into(binding.contractImageView);
            }
        });

        databaseReference.child("name").get().addOnSuccessListener(snapshot -> {
            String name = snapshot.getValue(String.class);
            if (name != null) {
                binding.nameTextView.setText(name);
            }
        });

    }

    private void updateProfileData() {
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Updating profile...");
        progressDialog.show();

        // Update ID photo if changed
        if (idImageUri != null) {
            uploadImage("idImages/" + user.getUid(), idImageUri, "idPhoto");
        }

        // Update contract photo if changed
        if (contractImageUri != null) {
            uploadImage("contractImages/" + user.getUid(), contractImageUri, "contract");
        }

        progressDialog.dismiss();
    }

    private void uploadImage(String path, Uri imageUri, String databaseField) {
        StorageReference imageRef = storageReference.child(path);
        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            databaseReference.child(databaseField).setValue(uri.toString())
                                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Image updated successfully!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update image URL: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    private void logout() {
        FirebaseAuth.getInstance().signOut();

        Toast.makeText(getActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getActivity(), SelectActivity.class);
        startActivity(intent);
        getActivity().finish();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
