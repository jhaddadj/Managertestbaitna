package com.example.manager.timetable;

/**
 Represents a single session in a timetable lecture, lab, et
 */
public class TimetableSession {
    private String id;
    private String courseId;
    private String courseName;
    private String lecturerId;
    private String lecturerName;
    private String resourceId;
    private String resourceName;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private String sessionType; // Lecture, Lab, Tutorial, etc.
    private String timetableId; // Reference to parent timetable
    private String uniqueSessionKey; // Unique identifier for deduplication
    private String department; // Department this session belongs to
    
    // Empty constructor for Firebase
    public TimetableSession() {
    }
    
    public TimetableSession(String id, String courseId, String courseName, String lecturerId, 
            String lecturerName, String resourceId, String resourceName, String dayOfWeek, 
            String startTime, String endTime, String sessionType) {
        this.id = id;
        this.courseId = courseId;
        this.courseName = courseName;
        this.lecturerId = lecturerId;
        this.lecturerName = lecturerName;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sessionType = sessionType;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCourseId() {
        return courseId;
    }
    
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }
    
    public String getCourseName() {
        return courseName;
    }
    
    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }
    
    public String getLecturerId() {
        return lecturerId;
    }
    
    public void setLecturerId(String lecturerId) {
        this.lecturerId = lecturerId;
    }
    
    public String getLecturerName() {
        return lecturerName;
    }
    
    public void setLecturerName(String lecturerName) {
        this.lecturerName = lecturerName;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getDayOfWeek() {
        return dayOfWeek;
    }
    
    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }
    
    public String getStartTime() {
        return startTime;
    }
    
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public String getEndTime() {
        return endTime;
    }
    
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
    
    public String getSessionType() {
        return sessionType;
    }
    
    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }
    
    public String getTimetableId() {
        return timetableId;
    }
    
    public void setTimetableId(String timetableId) {
        this.timetableId = timetableId;
    }
    
    public String getUniqueSessionKey() {
        return uniqueSessionKey;
    }
    
    public void setUniqueSessionKey(String uniqueSessionKey) {
        this.uniqueSessionKey = uniqueSessionKey;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    /**
     * Check if this session overlaps with another session
     * 
     * @param other The other session to check against
     * @return true if sessions overlap, false otherwise
     */
    public boolean overlapsWith(TimetableSession other) {
        // Only sessions on the same day can overlap
        if (!this.dayOfWeek.equals(other.dayOfWeek)) {
            return false;
        }
        
        // Convert time strings to comparable values
        // This is a simplified approach - in a real implementation you might want to use
        // proper time objects or integers representing minutes since midnight
        return !(this.endTime.compareTo(other.startTime) <= 0 || 
                 this.startTime.compareTo(other.endTime) >= 0);
    }
}
