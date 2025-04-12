package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;

public class TimetableInitializationActivity extends AppCompatActivity {
    
    private Spinner departmentSpinner;
    private Button proceedButton;
    private Button viewDepartmentTimetablesButton;
    
    // List of departments
    private final String[] departments = {"Computer Science", "Information Technology", "Engineering", "Business"};
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timetable_initialization);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        initializeUI();
        
        // Setup the department spinner
        setupDepartmentSpinner();
        
        // Setup buttons
        setupButtons();
    }
    
    private void initializeUI() {
        // Set mode description
        TextView modeDescriptionText = findViewById(R.id.modeDescriptionText);
        modeDescriptionText.setText("Constraint Solver automatically generates optimal timetables based on resources, lecturers, and course constraints. It uses advanced constraint programming to ensure the best possible schedule.");
        
        // Get UI references
        departmentSpinner = findViewById(R.id.departmentSpinner);
        proceedButton = findViewById(R.id.proceedButton);
        viewDepartmentTimetablesButton = findViewById(R.id.viewDepartmentTimetablesButton);
    }
    
    private void setupDepartmentSpinner() {
        // Create adapter for department spinner
        ArrayAdapter<String> departmentAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, departments);
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        departmentSpinner.setAdapter(departmentAdapter);
    }
    
    private void setupButtons() {
        // Setup proceed button to generate new timetable
        proceedButton.setOnClickListener(v -> {
            // Get selected department
            String selectedDepartment = departmentSpinner.getSelectedItem().toString();
            
            // Create intent and pass selected department
            Intent intent = new Intent(this, ConstraintSolverActivity.class);
            intent.putExtra("department", selectedDepartment);
            startActivity(intent);
        });
        
        // Setup view department timetables button
        viewDepartmentTimetablesButton.setOnClickListener(v -> {
            // Navigate to department timetables view
            Intent intent = new Intent(this, DepartmentTimetablesActivity.class);
            startActivity(intent);
        });
    }
}