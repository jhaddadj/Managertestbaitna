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
 * Use the {@link LectureListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LectureListFragment extends Fragment {
    private RecyclerView recyclerView;
    private LectureAdapter lectureAdapter;
    private DatabaseReference lectureDatabaseReference;
    private ProgressBar progressBar;

    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public LectureListFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the LectureListFragment with specified parameters
     */
    public static LectureListFragment newInstance(String param1, String param2) {
        LectureListFragment fragment = new LectureListFragment();
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
        View rootView = inflater.inflate(R.layout.fragment_lecture_list, container, false);

        recyclerView = rootView.findViewById(R.id.recyclerViewUser);
        progressBar=rootView.findViewById(R.id.progressBar);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        lectureAdapter = new LectureAdapter(new ArrayList<>());
        recyclerView.setAdapter(lectureAdapter);

        lectureDatabaseReference = FirebaseDatabase.getInstance().getReference("Users");

        // Fetch lectures from Firebase
        fetchPendingUsers();

        return rootView;
    }

    private void fetchPendingUsers() {
        progressBar.setVisibility(View.VISIBLE);

        lectureDatabaseReference.orderByChild("status").equalTo("pending")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<User> pendingUsers = new ArrayList<>();
                        pendingUsers.clear();
                        progressBar.setVisibility(View.INVISIBLE);

                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            User user = dataSnapshot.getValue(User.class);
                            if (user != null && "lecture".equals(user.getRole())) {
                                pendingUsers.add(user);
                            }
                        }

                        lectureAdapter.setLectureList(pendingUsers);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.INVISIBLE);

                        Toast.makeText(getContext(), "Failed to load users", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private class LectureAdapter extends RecyclerView.Adapter<LectureAdapter.LectureViewHolder> {
        private List<User> lectureList;

        public LectureAdapter(List<User> lectureList) {
            this.lectureList = lectureList;
        }

        @NonNull
        @Override
        public LectureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_user, parent, false);
            return new LectureViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LectureViewHolder holder, int position) {
            User lecture = lectureList.get(position);
            holder.lectureNameTextView.setText(lecture.getName());

            // Handle click to view lecture details
            holder.itemView.setOnClickListener(v -> {
                // Show lecture details in a new activity or dialog
                Intent intent = new Intent(getContext(), UserDetailActivity.class);
                intent.putExtra("id", lecture.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return lectureList.size();
        }

        private class LectureViewHolder extends RecyclerView.ViewHolder {
            TextView lectureNameTextView;

            public LectureViewHolder(View itemView) {
                super(itemView);
                lectureNameTextView = itemView.findViewById(R.id.nameTextView);
            }
        }

        public void setLectureList(List<User> lectureList) {
            this.lectureList = lectureList;
            notifyDataSetChanged();
        }
    }
}