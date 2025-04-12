package com.example.manager.stduent.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.manager.R;
import com.example.manager.admin.adapter.TimetableAdapter;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.databinding.FragmentHome2Binding;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.example.manager.admin.ui.ViewTimetableActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {
    private FragmentHome2Binding binding;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private DatabaseReference databaseReferenceUser;

    private List<TimetableEntry> timetableEntries = new ArrayList<>();
    private List<TimetableEntry> filteredEntries = new ArrayList<>();
    private TimetableAdapter adapter;
    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the HomeFragment with specified parameters
     */
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
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

        auth = FirebaseAuth.getInstance();
        databaseReferenceUser = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference = FirebaseDatabase.getInstance().getReference("timetables");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentHome2Binding.inflate(inflater, container, false);
        binding.timetableRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new TimetableAdapter(filteredEntries, this::onItemClicked, this::onItemLongClicked);
        binding.timetableRecyclerView.setAdapter(adapter);
        binding.textView.setVisibility(View.GONE);
        binding.filterButton.setVisibility(View.GONE);
        binding.viewTimetableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTimetableView();
            }
        });
        setupSearchView();
        return binding.getRoot();    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAccountStatus();
    }

    private void checkAccountStatus() {
        String userId = auth.getCurrentUser().getUid();

        // Fetch user data from Realtime Database
        databaseReferenceUser.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);

                    if ("rejected".equalsIgnoreCase(status)) {
                        showRejectionLayout();
                    } else if ("pending".equalsIgnoreCase(status)) {
                        showPendingLayout();
                    } else if ("accepted".equalsIgnoreCase(status)) {
                        // Hide overlay if status is accepted
                        if (binding != null) {
                            binding.overlayLayout.setVisibility(View.GONE);
                            binding.timetableRecyclerView.setVisibility(View.VISIBLE);
                            binding.upperLayout.setVisibility(View.VISIBLE);
                        }
                        loadTimetableData();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
            }
        });
    }

    private void showFilterDialog() {
        // Filter functionality removed
    }

    private void filterTimetable() {
        filteredEntries.clear();
        filteredEntries.addAll(timetableEntries);
        adapter.notifyDataSetChanged();
    }

    private boolean matchesFilter(TimetableEntry entry, String startDate, String endDate, String startTime, String endTime) {
        return true;
    }

    private boolean isWithinDateRange(String entryStartDate, String entryEndDate, String filterStartDate, String filterEndDate) {
        return true;
    }

    private boolean isDaySelected(List<String> entryDays, boolean monday, boolean tuesday, boolean wednesday, boolean thursday, boolean friday) {
        return true;
    }

    private boolean isWithinTimeRange(String timeSlot, String filterStartTime, String filterEndTime) {
        return true;
    }

    private void loadTimetableData() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // Check if binding is null (Fragment might have been destroyed)
                if (binding == null) {
                    return;
                }

                timetableEntries.clear();
                filteredEntries.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    TimetableEntry entry = data.getValue(TimetableEntry.class);
                    if (entry != null) {
                        binding.progressBar.setVisibility(View.INVISIBLE);

                        entry.setCourseId(data.getKey());

                        timetableEntries.add(entry);

                    } else {

                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.textView.setVisibility(View.VISIBLE);

                    }
                }
                filteredEntries.addAll(timetableEntries);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (binding != null) {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                }
                Toast.makeText(getActivity(), "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterTimetable(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterTimetable(newText);
                return false;
            }
        });
    }

    private void filterTimetable(String query) {
        filteredEntries.clear();
        if (query == null || query.isEmpty()) {
            filteredEntries.addAll(timetableEntries);
        } else {
            query = query.toLowerCase();
            for (TimetableEntry entry : timetableEntries) {
                if (entry.getCourseId().toLowerCase().contains(query) ||
                        entry.getCourseName().toLowerCase().contains(query) ||
                        entry.getLecturerName().toLowerCase().contains(query)) {
                    filteredEntries.add(entry);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onItemClicked(TimetableEntry entry) {

        Intent intent = new Intent(getActivity(), ViewScheduleStudentActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }

    private void onItemLongClicked(TimetableEntry entry) {
        Intent intent = new Intent(getActivity(), ViewScheduleStudentActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }

    private void showRejectionLayout() {
        if (binding != null) {
            binding.overlayLayout.setVisibility(View.VISIBLE);
            binding.overlayMessageTextView.setText("Your account has been rejected by the admin. Please re-upload your correct ID and contract with clear images. If the issue continues, contact the admin department.");
        }
    }

    private void showPendingLayout() {
        if (binding != null) {
            binding.overlayLayout.setVisibility(View.VISIBLE);
            binding.overlayMessageTextView.setText("Your account is waiting for admin approval. Please wait until further notification.");
        }
    }

    private void openTimetableView() {
        String userId = auth.getCurrentUser().getUid();

        // Show progress while loading
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        // First check in the student_departments node for department information
        DatabaseReference studentDeptRef = FirebaseDatabase.getInstance()
                .getReference("student_departments").child(userId);

        studentDeptRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.child("department").exists()) {
                    String department = snapshot.child("department").getValue(String.class);

                    if (department != null && !department.isEmpty()) {
                        // Query for department's timetable ID
                        DatabaseReference deptTimetablesRef = FirebaseDatabase.getInstance()
                                .getReference("department_timetables").child(department);

                        deptTimetablesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }

                                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                    // Find the most recent timetable ID
                                    String timetableId = null;
                                    long latestTime = 0;

                                    for (DataSnapshot timetableSnapshot : dataSnapshot.getChildren()) {
                                        String id = timetableSnapshot.getKey();
                                        Object timestamp = timetableSnapshot.child("timestamp").getValue();

                                        if (timestamp != null) {
                                            long time;
                                            try {
                                                time = Long.parseLong(timestamp.toString());
                                            } catch (NumberFormatException e) {
                                                time = 0;
                                            }

                                            if (time > latestTime) {
                                                latestTime = time;
                                                timetableId = id;
                                            }
                                        } else if (timetableId == null) {
                                            // If no timestamp, just take the first one
                                            timetableId = id;
                                        }
                                    }

                                    if (timetableId != null) {
                                        // Open the admin's timetable view activity with the timetable ID
                                        Intent intent = new Intent(getActivity(), ViewTimetableActivity.class);
                                        intent.putExtra("timetableId", timetableId);
                                        intent.putExtra("department", department);
                                        startActivity(intent);
                                    } else {
                                        Toast.makeText(getActivity(), "No valid timetable found for your department", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(getActivity(), "No timetable has been generated for your department yet", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                        }
                        Toast.makeText(getActivity(), "No department information found in your profile", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Fallback - try to check if department is directly in the user node
                    checkDepartmentInUserNode(userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkDepartmentInUserNode(String userId) {
        // Get student's department to filter timetable
        databaseReferenceUser.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String department = snapshot.child("department").getValue(String.class);
                    if (department != null && !department.isEmpty()) {
                        // Query for department's timetable ID
                        DatabaseReference deptTimetablesRef = FirebaseDatabase.getInstance()
                                .getReference("department_timetables").child(department);

                        deptTimetablesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }

                                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                    // Find the most recent timetable ID
                                    String timetableId = null;
                                    long latestTime = 0;

                                    for (DataSnapshot timetableSnapshot : dataSnapshot.getChildren()) {
                                        String id = timetableSnapshot.getKey();
                                        Object timestamp = timetableSnapshot.child("timestamp").getValue();

                                        if (timestamp != null) {
                                            long time;
                                            try {
                                                time = Long.parseLong(timestamp.toString());
                                            } catch (NumberFormatException e) {
                                                time = 0;
                                            }

                                            if (time > latestTime) {
                                                latestTime = time;
                                                timetableId = id;
                                            }
                                        } else if (timetableId == null) {
                                            // If no timestamp, just take the first one
                                            timetableId = id;
                                        }
                                    }

                                    if (timetableId != null) {
                                        // Open the admin's timetable view activity with the timetable ID
                                        Intent intent = new Intent(getActivity(), ViewTimetableActivity.class);
                                        intent.putExtra("timetableId", timetableId);
                                        intent.putExtra("department", department);
                                        startActivity(intent);
                                    } else {
                                        Toast.makeText(getActivity(), "No valid timetable found for your department", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(getActivity(), "No timetable has been generated for your department yet", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                        }
                        Toast.makeText(getActivity(), "Department information not found in your profile", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (binding != null) {
                        binding.progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(getActivity(), "Could not retrieve department information", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }
}