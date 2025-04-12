package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.manager.R;
import com.example.manager.model.User;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PendingUserListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PendingUserListFragment extends Fragment {
    private RecyclerView recyclerView;
    private UserAdapter userAdapter;
    private DatabaseReference databaseReference;
    private ProgressBar progressBar;
    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public PendingUserListFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the PendingUserListFragment with specified parameters
     */
    public static PendingUserListFragment newInstance(String param1, String param2) {
        PendingUserListFragment fragment = new PendingUserListFragment();
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_pending_user_list, container, false);

        recyclerView = rootView.findViewById(R.id.recyclerViewUser);
        progressBar=rootView.findViewById(R.id.progressBar);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        userAdapter = new UserAdapter(new ArrayList<>());
        recyclerView.setAdapter(userAdapter);

        databaseReference = FirebaseDatabase.getInstance().getReference("Users");

        // Fetch pending users from Firebase
        fetchPendingUsers();

        return rootView;

    }

    private void fetchPendingUsers() {
       progressBar.setVisibility(View.VISIBLE);

        databaseReference.orderByChild("status").equalTo("pending")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<User> pendingUsers = new ArrayList<>();
                        pendingUsers.clear();
                        progressBar.setVisibility(View.INVISIBLE);

                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            User user = dataSnapshot.getValue(User.class);
                            if (user != null && "student".equals(user.getRole())) {
                                pendingUsers.add(user);
                            }
                        }
                        userAdapter.setUserList(pendingUsers);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.INVISIBLE);

                        Toast.makeText(getContext(), "Failed to load users", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private List<User> userList;

        public UserAdapter(List<User> userList) {
            this.userList = userList;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            User user = userList.get(position);
            holder.nameTextView.setText(user.getName());

            holder.itemView.setOnClickListener(v -> {
                // Show lecture details in a new activity or dialog
                Intent intent = new Intent(getContext(), UserDetailActivity.class);
                intent.putExtra("id", user.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return userList.size();
        }

        private class UserViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;


            public UserViewHolder(View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.nameTextView);

            }
        }

        public void setUserList(List<User> userList) {
            this.userList = userList;
            notifyDataSetChanged();
        }
    }

    private void updateUserStatus(User user, String status) {
        DatabaseReference userRef = databaseReference.child(user.getId());
        userRef.child("status").setValue(status)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "User " + status, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to update status", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}