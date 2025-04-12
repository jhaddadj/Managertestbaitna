package com.example.manager.lecturar.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.manager.R;
import com.example.manager.admin.adapter.TimetableAdapter;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.admin.ui.AddScheduleActivity;
import com.example.manager.admin.ui.TimetableInitializationActivity;
import com.example.manager.admin.ui.ViewTimetableActivity;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.example.manager.databinding.FragmentHomeBinding;
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
    private FragmentHomeBinding binding;
    private DatabaseReference databaseReferenceUser;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private List<TimetableEntry> timetableEntries = new ArrayList<>();
    private TimetableAdapter adapter;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters for navigation
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
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Setup RecyclerView
        binding.timetableRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new TimetableAdapter(timetableEntries, this::onItemClicked, this::onItemLongClicked);
        binding.timetableRecyclerView.setAdapter(adapter);
        binding.textView.setVisibility(View.GONE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAccountStatus();
        
        // Setup View Timetable button click
        binding.viewTimetableButton.setOnClickListener(v -> openTimetableView());
    }

    private void openTimetableView() {
        String userId = auth.getCurrentUser().getUid();
        
        // Show progress while loading
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        // First check in the lecturer_departments node for department information
        DatabaseReference lecturerDeptRef = FirebaseDatabase.getInstance()
                .getReference("lecturer_departments").child(userId);
        
        lecturerDeptRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.child("departments").exists()) {
                    // Get all departments from the list
                    DataSnapshot departmentsSnapshot = snapshot.child("departments");
                    List<String> departments = new ArrayList<>();
                    
                    // Try to get all departments from the list
                    if (departmentsSnapshot.getChildrenCount() > 0) {
                        for (DataSnapshot deptSnapshot : departmentsSnapshot.getChildren()) {
                            departments.add(deptSnapshot.getValue(String.class));
                        }
                    }
                    
                    if (!departments.isEmpty()) {
                        // Create a new Intent for the combined timetable view
                        Intent combinedIntent = new Intent(getActivity(), com.example.manager.admin.ui.ViewTimetableActivity.class);
                        
                        // Pass the lecturer ID to filter the timetable
                        combinedIntent.putExtra("lecturerId", userId);
                        
                        // Put all departments in the intent as an ArrayList
                        combinedIntent.putStringArrayListExtra("allDepartments", new ArrayList<>(departments));
                        
                        // Set a flag to indicate we're showing a multi-department view
                        combinedIntent.putExtra("isMultiDepartment", true);
                        
                        // Start loading each department's timetable (we'll still display the first one's UI)
                        loadDepartmentTimetable(departments.get(0), combinedIntent, 0, departments.size());
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(getActivity(), "No departments assigned to you", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getActivity(), "No departments found for your account", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Loads timetable IDs for a specific department, then proceeds to the next department
     */
    private void loadDepartmentTimetable(String department, Intent combinedIntent, int currentIndex, int totalDepartments) {
        // Query for department's timetable ID
        DatabaseReference deptTimetablesRef = FirebaseDatabase.getInstance()
                .getReference("department_timetables").child(department);
        
        deptTimetablesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                // Find the most recent timetable ID for this department
                String timetableId = null;
                long latestTime = 0;
                
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
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
                }
                
                // Store the timetable ID for this department
                if (timetableId != null) {
                    // Get current list or create new one
                    ArrayList<String> timetableIds = combinedIntent.getStringArrayListExtra("timetableIds");
                    if (timetableIds == null) {
                        timetableIds = new ArrayList<>();
                    }
                    timetableIds.add(timetableId);
                    combinedIntent.putStringArrayListExtra("timetableIds", timetableIds);
                    
                    // If first department, set it as the primary one (for UI title purposes)
                    if (currentIndex == 0) {
                        combinedIntent.putExtra("timetableId", timetableId);
                        combinedIntent.putExtra("department", department);
                    }
                }
                
                // Move to the next department or finish
                int nextIndex = currentIndex + 1;
                if (nextIndex < totalDepartments) {
                    // Process next department
                    ArrayList<String> allDepartments = combinedIntent.getStringArrayListExtra("allDepartments");
                    if (allDepartments != null && nextIndex < allDepartments.size()) {
                        loadDepartmentTimetable(allDepartments.get(nextIndex), combinedIntent, nextIndex, totalDepartments);
                    }
                } else {
                    // We've processed all departments, now start the activity
                    binding.progressBar.setVisibility(View.GONE);
                    
                    ArrayList<String> timetableIds = combinedIntent.getStringArrayListExtra("timetableIds");
                    if (timetableIds != null && !timetableIds.isEmpty()) {
                        startActivity(combinedIntent);
                    } else {
                        Toast.makeText(getActivity(), "No timetables found for your departments", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Error loading timetable: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void checkDepartmentInUserNode(String userId) {
        // Get lecturer's department to filter timetable
        databaseReferenceUser.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                if (snapshot.exists()) {
                    String department = snapshot.child("department").getValue(String.class);
                    if (department != null && !department.isEmpty()) {
                        // Query for department's timetable ID
                        DatabaseReference deptTimetablesRef = FirebaseDatabase.getInstance()
                                .getReference("department_timetables").child(department);
                        
                        deptTimetablesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (binding == null) {
                                    return; // Fragment is detached, don't proceed
                                }
                                
                                binding.progressBar.setVisibility(View.GONE);

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
                                        
                                        // Pass the lecturer ID to filter the timetable
                                        intent.putExtra("lecturerId", userId);
                                        
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
                                if (binding == null) {
                                    return; // Fragment is detached, don't proceed
                                }
                                
                                binding.progressBar.setVisibility(View.GONE);
                                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        if (binding == null) {
                            return; // Fragment is detached, don't proceed
                        }
                        
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(getActivity(), "Department information not found in your profile", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (binding == null) {
                        return; // Fragment is detached, don't proceed
                    }
                    
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getActivity(), "Could not retrieve department information", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAccountStatus() {
        databaseReferenceUser.child(auth.getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    
                    if ("rejected".equalsIgnoreCase(status)) {
                        showRejectionLayout();
                    } else if ("pending".equalsIgnoreCase(status)) {
                        showPendingLayout();
                    } else if ("accepted".equalsIgnoreCase(status)) {
                        // Hide overlay if status is accepted
                        binding.overlayLayout.setVisibility(View.GONE);
                        binding.timetableRecyclerView.setVisibility(View.VISIBLE);
                        loadTimetableData();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }
    
    private void loadTimetableData() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        
        String userId = auth.getCurrentUser().getUid();
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null) {
                    return; // Fragment is detached, don't proceed
                }
                
                timetableEntries.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    TimetableEntry entry = data.getValue(TimetableEntry.class);
                    if (entry != null) {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.INVISIBLE);
                        }
                        
                        if (userId.equalsIgnoreCase(entry.getLecturerId())) {
                            entry.setCourseId(data.getKey());
                            timetableEntries.add(entry);
                        }
                    } else {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.INVISIBLE);
                        }
                        if (binding != null) {
                            binding.textView.setVisibility(View.VISIBLE);
                        }
                    }
                }
                
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
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

    private void onItemClicked(TimetableEntry entry) {
//        Intent intent = new Intent(this, AddScheduleActivity.class);
        Intent intent = new Intent(getActivity(), ViewScheduleActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void onItemLongClicked(TimetableEntry entry) {
        Intent intent = new Intent(getActivity(), ViewScheduleActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void showRejectionLayout() {
        if (binding == null) {
            return; // Fragment is detached, don't proceed
        }
        
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account has been rejected by the admin. Please re-upload your correct ID and contract with clear images. If the issue continues, contact the admin department.");
    }

    private void showPendingLayout() {
        if (binding == null) {
            return; // Fragment is detached, don't proceed
        }
        
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account is waiting for admin approval. Please wait until further notification.");
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Set binding to null to avoid memory leaks
        binding = null;
    }
}