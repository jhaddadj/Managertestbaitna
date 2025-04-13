package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Activity that displays all the timetables for each department
 * and allows viewing or generating new timetables.
 */
public class DepartmentTimetablesActivity extends AppCompatActivity {
    private static final String TAG = "DeptTimetablesActivity";
    private LinearLayout departmentTimetablesContainer;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private Button backButton;
    private Button generateAllButton;
    
    // List of departments
    private final String[] departments = {"Computer Science", "Information Technology", "Engineering", "Business"};
    
    // Track timetable IDs for each department
    private Map<String, String> departmentTimetableIds = new HashMap<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_department_timetables);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize UI components
        departmentTimetablesContainer = findViewById(R.id.departmentTimetablesContainer);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        backButton = findViewById(R.id.backButton);
        generateAllButton = findViewById(R.id.generateAllButton);
        
        backButton.setOnClickListener(v -> finish());
        
        // Set up generate all button
        generateAllButton.setOnClickListener(v -> {
            Intent intent = new Intent(DepartmentTimetablesActivity.this, UnifiedTimetableGeneratorActivity.class);
            startActivity(intent);
        });
        
        setupDepartmentTimetablesView();
    }
    
    private void setupDepartmentTimetablesView() {
        // Initialize views
        departmentTimetablesContainer = findViewById(R.id.departmentTimetablesContainer);
        
        // Add "View All Departments" button programmatically (after the generate button)
        Button viewAllDepartmentsButton = new Button(this);
        viewAllDepartmentsButton.setText("View All Departments Combined");
        viewAllDepartmentsButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        viewAllDepartmentsButton.setPadding(20, 20, 20, 20);
        viewAllDepartmentsButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
        viewAllDepartmentsButton.setTextColor(getResources().getColor(android.R.color.white));
        
        // Set click listener for the "View All Departments" button
        viewAllDepartmentsButton.setOnClickListener(v -> {
            // Check if we have at least one department with a timetable
            boolean hasTimetable = false;
            for (String timetableId : departmentTimetableIds.values()) {
                if (timetableId != null && !timetableId.isEmpty()) {
                    hasTimetable = true;
                    break;
                }
            }
            
            if (!hasTimetable) {
                Toast.makeText(this, "No timetables available. Generate timetables first.", 
                    Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Launch the consolidated view with all departments
            openConsolidatedTimetableView();
        });
        
        // Safety check: first make sure the container isn't empty
        if (departmentTimetablesContainer.getChildCount() == 0) {
            // If the container is empty, just add at index 0
            departmentTimetablesContainer.addView(viewAllDepartmentsButton, 0);
        } else {
            // Find the index where to insert the new button (after the "Generate All Department Timetables" button)
            int insertIndex = 0; // Default to the first position
            
            // Iterate through departmentTimetablesContainer children to find the generate button
            boolean foundGenerateButton = false;
            for (int i = 0; i < departmentTimetablesContainer.getChildCount(); i++) {
                View child = departmentTimetablesContainer.getChildAt(i);
                if (child instanceof Button) {
                    Button button = (Button) child;
                    String buttonText = button.getText().toString();
                    if (buttonText.contains("Generate All Department")) {
                        insertIndex = i + 1;
                        foundGenerateButton = true;
                        break;
                    }
                }
            }
            
            // Add the button at the appropriate position
            if (insertIndex < departmentTimetablesContainer.getChildCount()) {
                departmentTimetablesContainer.addView(viewAllDepartmentsButton, insertIndex);
            } else {
                // If we couldn't find a valid index or it would be out of bounds, add at the end
                departmentTimetablesContainer.addView(viewAllDepartmentsButton);
            }
        }
        
        // Load department timetables
        loadDepartmentTimetables();
    }
    
    private void loadDepartmentTimetables() {
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText("Loading department timetables...");
        statusTextView.setVisibility(View.VISIBLE);
        
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        
        // Check each department for timetables
        for (String department : departments) {
            createDepartmentCard(department);
            
            DatabaseReference deptRef = database.getReference("department_timetables").child(department);
            deptRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    updateDepartmentCardWithTimetableInfo(department, dataSnapshot);
                }
                
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error loading timetables for " + department, databaseError.toException());
                }
            });
        }
        
        progressBar.setVisibility(View.GONE);
        statusTextView.setVisibility(View.GONE);
    }
    
    private void createDepartmentCard(String department) {
        // Create a card for this department
        View departmentCard = getLayoutInflater().inflate(R.layout.card_department_timetable, departmentTimetablesContainer, false);
        
        // Set department name
        TextView departmentNameTextView = departmentCard.findViewById(R.id.departmentNameTextView);
        departmentNameTextView.setText(department);
        
        // Set initial status
        TextView timetableStatusTextView = departmentCard.findViewById(R.id.timetableStatusTextView);
        timetableStatusTextView.setText("Checking for existing timetables...");
        
        // Set buttons to initially disabled
        Button viewTimetableButton = departmentCard.findViewById(R.id.viewTimetableButton);
        viewTimetableButton.setEnabled(false);
        
        // Set tag for future reference
        departmentCard.setTag(department);
        
        // Add the card to the container
        departmentTimetablesContainer.addView(departmentCard);
    }
    
    private void updateDepartmentCardWithTimetableInfo(String department, DataSnapshot dataSnapshot) {
        // Find the card for this department
        for (int i = 0; i < departmentTimetablesContainer.getChildCount(); i++) {
            View childView = departmentTimetablesContainer.getChildAt(i);
            if (childView.getTag() != null && childView.getTag().equals(department)) {
                TextView timetableStatusTextView = childView.findViewById(R.id.timetableStatusTextView);
                Button viewTimetableButton = childView.findViewById(R.id.viewTimetableButton);
                
                // First, check if there's a default timetable ID for this department
                DatabaseReference defaultTimetableRef = FirebaseDatabase.getInstance().getReference()
                        .child("default_timetables").child(department);
                
                defaultTimetableRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot defaultSnapshot) {
                        String timetableId = null;
                        
                        if (defaultSnapshot.exists()) {
                            // Use the default timetable ID if available
                            timetableId = defaultSnapshot.getValue(String.class);
                            Log.d(TAG, "Found default timetable ID for " + department + ": " + timetableId);
                        } else if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                            // Fall back to the most recent timetable by timestamp if no default is set
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
                            Log.d(TAG, "Using most recent timetable for " + department + ": " + timetableId);
                        }
                        
                        if (timetableId != null) {
                            // Before showing as available, verify there are actual sessions for this timetable
                            validateTimetableSessions(department, timetableId, childView);
                        } else {
                            timetableStatusTextView.setText("No timetable available - use Generate All Timetables");
                            viewTimetableButton.setEnabled(false);
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error fetching default timetable for " + department, error.toException());
                        timetableStatusTextView.setText("Error loading timetable information");
                        viewTimetableButton.setEnabled(false);
                    }
                });
                
                break;
            }
        }
    }
    
    /**
     * Validates that a timetable ID actually has associated sessions before marking it as available
     */
    private void validateTimetableSessions(String department, String timetableId, View departmentCard) {
        TextView timetableStatusTextView = departmentCard.findViewById(R.id.timetableStatusTextView);
        Button viewTimetableButton = departmentCard.findViewById(R.id.viewTimetableButton);
        timetableStatusTextView.setText("Verifying timetable...");
        
        // Reference to the department-specific timetable sessions
        DatabaseReference sessionsRef = FirebaseDatabase.getInstance().getReference()
                .child("department_timetableSessions")
                .child(department);
        
        // Query to check for sessions with this timetable ID
        sessionsRef.orderByChild("timetableId").equalTo(timetableId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                            // Sessions exist for this timetable, so it's valid
                            Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " valid sessions for timetable " + timetableId + " in " + department);
                            departmentTimetableIds.put(department, timetableId);
                            timetableStatusTextView.setText("Timetable available (" + dataSnapshot.getChildrenCount() + " sessions)");
                            viewTimetableButton.setEnabled(true);
                            
                            // Set up view button click listener with the timetable ID
                            viewTimetableButton.setOnClickListener(v -> {
                                Intent intent = new Intent(DepartmentTimetablesActivity.this, ViewTimetableActivity.class);
                                intent.putExtra("timetableId", timetableId);
                                intent.putExtra("department", department);
                                startActivity(intent);
                            });
                        } else {
                            // No sessions found for this timetable ID, so it's invalid or empty
                            Log.w(TAG, "No sessions found for timetable " + timetableId + " in " + department);
                            
                            // Before giving up, check if there are ANY sessions for this department, regardless of timetable ID
                            checkForAnyTimetableSessions(department, departmentCard);
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error validating timetable sessions for " + department, databaseError.toException());
                        timetableStatusTextView.setText("Error verifying timetable");
                        viewTimetableButton.setEnabled(false);
                    }
                });
    }
    
    /**
     * Checks if there are any timetable sessions for a department, regardless of timetable ID
     * This is a fallback in case the timetable ID references are out of sync
     */
    private void checkForAnyTimetableSessions(String department, View departmentCard) {
        TextView timetableStatusTextView = departmentCard.findViewById(R.id.timetableStatusTextView);
        Button viewTimetableButton = departmentCard.findViewById(R.id.viewTimetableButton);
        
        // Reference to all sessions for this department
        DatabaseReference allSessionsRef = FirebaseDatabase.getInstance().getReference()
                .child("department_timetableSessions")
                .child(department);
        
        allSessionsRef.limitToFirst(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    // There are sessions, but they don't match the expected timetable ID
                    // Extract the actual timetable ID from the first session
                    String foundTimetableId = null;
                    
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        String sessionTimetableId = sessionSnapshot.child("timetableId").getValue(String.class);
                        if (sessionTimetableId != null && !sessionTimetableId.isEmpty()) {
                            foundTimetableId = sessionTimetableId;
                            break;
                        }
                    }
                    
                    if (foundTimetableId != null) {
                        // Found a valid timetable ID, update references and enable viewing
                        Log.d(TAG, "Found alternate timetable ID " + foundTimetableId + " for " + department);
                        
                        // Make a final copy for use in lambdas
                        final String finalTimetableId = foundTimetableId;
                        
                        // Update the default timetable reference to point to this timetable
                        FirebaseDatabase.getInstance().getReference()
                                .child("default_timetables")
                                .child(department)
                                .setValue(finalTimetableId)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Updated default timetable reference for " + department + " to " + finalTimetableId);
                                    
                                    // Now validate this timetable ID
                                    validateTimetableSessions(department, finalTimetableId, departmentCard);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update default timetable reference: " + e.getMessage());
                                    // Continue with the found ID anyway
                                    validateTimetableSessions(department, finalTimetableId, departmentCard);
                                });
                    } else {
                        // Sessions exist but no valid timetable ID found
                        timetableStatusTextView.setText("Invalid timetable data - regenerate timetable");
                        viewTimetableButton.setEnabled(false);
                    }
                } else {
                    // No sessions at all for this department
                    timetableStatusTextView.setText("No timetable available - use Generate All Timetables");
                    viewTimetableButton.setEnabled(false);
                    
                    // Clean up any default reference if it exists
                    FirebaseDatabase.getInstance().getReference()
                            .child("default_timetables")
                            .child(department)
                            .removeValue()
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleaned up default reference for empty department " + department));
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error checking for any sessions: " + databaseError.getMessage());
                timetableStatusTextView.setText("Error accessing timetable data");
                viewTimetableButton.setEnabled(false);
            }
        });
    }
    
    /**
     * Opens a consolidated view showing all departments' timetables combined
     */
    private void openConsolidatedTimetableView() {
        Intent intent = new Intent(this, ViewTimetableActivity.class);
        
        // Pass all departments to view
        intent.putExtra("isAllDepartmentsView", true);
        
        // Convert departments array to ArrayList for intent
        ArrayList<String> departmentsList = new ArrayList<>(Arrays.asList(departments));
        intent.putStringArrayListExtra("allDepartments", departmentsList);
        
        // Set flag to show all departments are being viewed
        intent.putExtra("isMultiDepartment", true);
        
        // Set a special title for this view
        intent.putExtra("customTitle", "All Departments - Consolidated View");
        
        startActivity(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload timetables when returning to this activity
        if (departmentTimetablesContainer != null) {
            departmentTimetablesContainer.removeAllViews();
            setupDepartmentTimetablesView();
        }
    }
}
//test everything downloaded ?