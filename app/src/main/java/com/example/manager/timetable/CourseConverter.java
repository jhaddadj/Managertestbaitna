package com.example.manager.timetable;

import com.example.manager.admin.model.CourseItem;
import android.util.Log;

/**
 * Utility class to convert between CourseItem and Course objects.
 * This bridges the gap between our course management system and the timetable generation system.
 */
public class CourseConverter {
    private static final String TAG = "CourseConverter";

    /**
     * Converts a CourseItem object to a Course object suitable for timetable generation.
     *
     * @param courseItem The CourseItem from the course management system
     * @return A Course object ready for timetable generation
     */
    public static Course convertToCourse(CourseItem courseItem) {
        if (courseItem == null) {
            Log.w(TAG, "Null courseItem provided");
            return null;
        }
        
        // Map fields from CourseItem to Course
        String id = courseItem.getId();
        String name = courseItem.getName();
        String code = courseItem.getCode();
        int durationHours = courseItem.getDurationHours();
        String department = courseItem.getDepartment();
        
        // Validate required fields
        if (id == null || id.isEmpty()) {
            Log.w(TAG, "Course has null or empty ID: " + name);
            id = "course_" + System.currentTimeMillis(); // Generate a temporary ID
        }
        
        if (name == null || name.isEmpty()) {
            Log.w(TAG, "Course has null or empty name, ID: " + id);
            name = "Unnamed Course";
        }
        
        if (code == null || code.isEmpty()) {
            Log.w(TAG, "Course has null or empty code: " + name);
            code = "CODE-" + id.substring(0, Math.min(5, id.length()));
        }
        
        // If durationHours is zero or negative, default to 1 hour
        if (durationHours <= 0) {
            Log.w(TAG, "Course '" + name + "' has invalid duration (" + durationHours + "). Setting to 1 hour.");
            durationHours = 1;
        }
        
        // Default department if missing
        if (department == null || department.isEmpty()) {
            Log.w(TAG, "Course '" + name + "' has no department. Using default.");
            department = "General";
        }
        
        // Get the assigned lecturer ID, use default if not set
        String assignedLecturerId = courseItem.getAssignedLecturerId();
        if (assignedLecturerId == null || assignedLecturerId.isEmpty()) {
            Log.w(TAG, "Course '" + name + "' has no assigned lecturer ID. Will be assigned during scheduling.");
            assignedLecturerId = "";  // Will be assigned during scheduling
        }
        
        // Get the assigned resource (room) ID, if any
        String assignedResourceId = courseItem.getAssignedResourceId();
        if (assignedResourceId == null || assignedResourceId.isEmpty()) {
            Log.d(TAG, "Course '" + name + "' has no assigned resource ID.");
        } else {
            Log.d(TAG, "Course '" + name + "' has assigned resource ID: " + assignedResourceId);
        }
        
        // Calculate total sessions required per week (lectures + labs)
        int lectures = courseItem.getNumberOfLectures();
        int labs = courseItem.getNumberOfLabs();
        
        // Guard against negative values
        lectures = Math.max(0, lectures);
        labs = Math.max(0, labs);
        
        int totalSessions = lectures + labs;
        
        // Ensure at least one session is allocated, even if both lectures and labs are zero or negative
        if (totalSessions <= 0) {
            totalSessions = 1;
            Log.w(TAG, "Course '" + name + "' had 0 sessions (lectures=" + 
                             courseItem.getNumberOfLectures() + ", labs=" + 
                             courseItem.getNumberOfLabs() + "). Setting to 1 default session.");
        }
        
        // Create a Course object with the total sessions
        Course course = new Course(id, name, code, durationHours, department, totalSessions);
        
        // Set required room type based on whether there are lab sessions
        if (labs > 0) {
            course.setRequiredRoomType("LAB");
            Log.d(TAG, "Course '" + name + "' requires a LAB room due to " + labs + " lab sessions");
        } else {
            course.setRequiredRoomType("LECTURE_HALL");
            Log.d(TAG, "Course '" + name + "' will use a LECTURE_HALL for its " + lectures + " lecture sessions");
        }
        
        // Set the assigned lecturer ID
        course.setAssignedLecturerId(assignedLecturerId);
        
        // Set the assigned resource ID (if specified)
        if (assignedResourceId != null && !assignedResourceId.isEmpty()) {
            course.setAssignedResourceId(assignedResourceId);
        }
        
        Log.d(TAG, "Converted course: " + name + 
                " with " + totalSessions + " sessions (lectures=" + 
                lectures + ", labs=" + 
                labs + "), required room type: " + course.getRequiredRoomType());
        
        return course;
    }
}
