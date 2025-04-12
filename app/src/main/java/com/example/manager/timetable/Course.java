package com.example.manager.timetable;

/**
 * Represents a course or subject in the academic system.
 * Contains information about the course requirements, such as
 * duration, preferred resources, eligible lecturers, etc.
 */
public class Course {
    private String id;
    private String name;
    private String code;
    private int creditHours;
    private String department;
    private int requiredSessionsPerWeek;
    private String requiredRoomType;
    private String assignedLecturerId; // ID of assigned lecturer from course management
    private String assignedResourceId; // ID of assigned resource (room) from course management
    private String lecturerName;      // Name of the assigned lecturer (for direct reference)
    private String assignedRoom;      // Name of the assigned room/resource (for direct reference)
    
    // New fields to match Firebase database
    private int durationHours;
    private int totalSessionsPerWeek;
    private boolean spreadCourseSessions;
    private int numberOfLectures;
    private String adminId;
    private int viewType;
    private int numberOfLabs;
    
    // Empty constructor for Firebase
    public Course() {
    }
    
    public Course(String id, String name, String code, int creditHours, String department, int requiredSessionsPerWeek) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = null; // Default to null
        this.assignedLecturerId = null; // Default to null
        this.assignedResourceId = null; // Default to null
        this.lecturerName = null;     // Default to null
        this.assignedRoom = null;     // Default to null
        this.durationHours = 0;       // Default to 0
        this.totalSessionsPerWeek = 0; // Default to 0
        this.spreadCourseSessions = false; // Default to false
        this.numberOfLectures = 0;    // Default to 0
        this.adminId = null;          // Default to null
        this.viewType = 0;            // Default to 0
        this.numberOfLabs = 0;        // Default to 0
    }
    
    public Course(String id, String name, String code, int creditHours, String department, int requiredSessionsPerWeek, String requiredRoomType) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = requiredRoomType;
        this.assignedLecturerId = null; // Default to null
        this.assignedResourceId = null; // Default to null
        this.lecturerName = null;     // Default to null
        this.assignedRoom = null;     // Default to null
        this.durationHours = 0;       // Default to 0
        this.totalSessionsPerWeek = 0; // Default to 0
        this.spreadCourseSessions = false; // Default to false
        this.numberOfLectures = 0;    // Default to 0
        this.adminId = null;          // Default to null
        this.viewType = 0;            // Default to 0
        this.numberOfLabs = 0;        // Default to 0
    }
    
    // Full constructor with assignedLecturerId and assignedResourceId
    public Course(String id, String name, String code, int creditHours, String department, 
                 int requiredSessionsPerWeek, String requiredRoomType, String assignedLecturerId, String assignedResourceId) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = requiredRoomType;
        this.assignedLecturerId = assignedLecturerId;
        this.assignedResourceId = assignedResourceId;
        this.lecturerName = null;     // Default to null
        this.assignedRoom = null;     // Default to null
        this.durationHours = 0;       // Default to 0
        this.totalSessionsPerWeek = 0; // Default to 0
        this.spreadCourseSessions = false; // Default to false
        this.numberOfLectures = 0;    // Default to 0
        this.adminId = null;          // Default to null
        this.viewType = 0;            // Default to 0
        this.numberOfLabs = 0;        // Default to 0
    }
    
    // Full constructor with all fields
    public Course(String id, String name, String code, int creditHours, String department, 
                 int requiredSessionsPerWeek, String requiredRoomType, String assignedLecturerId, 
                 String assignedResourceId, String lecturerName, String assignedRoom, int durationHours, 
                 int totalSessionsPerWeek, boolean spreadCourseSessions, int numberOfLectures, String adminId, 
                 int viewType, int numberOfLabs) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.creditHours = creditHours;
        this.department = department;
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
        this.requiredRoomType = requiredRoomType;
        this.assignedLecturerId = assignedLecturerId;
        this.assignedResourceId = assignedResourceId;
        this.lecturerName = lecturerName;
        this.assignedRoom = assignedRoom;
        this.durationHours = durationHours;
        this.totalSessionsPerWeek = totalSessionsPerWeek;
        this.spreadCourseSessions = spreadCourseSessions;
        this.numberOfLectures = numberOfLectures;
        this.adminId = adminId;
        this.viewType = viewType;
        this.numberOfLabs = numberOfLabs;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public int getCreditHours() {
        return creditHours;
    }
    
    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public int getRequiredSessionsPerWeek() {
        return requiredSessionsPerWeek;
    }
    
    public void setRequiredSessionsPerWeek(int requiredSessionsPerWeek) {
        this.requiredSessionsPerWeek = requiredSessionsPerWeek;
    }
    
    /**
     * Gets the required room type for this course (e.g., "LAB", "LECTURE_HALL", "SEMINAR_ROOM").
     * If a specific room type is not set, defaults to the department as a fallback.
     * 
     * @return The required room type, or null if no constraints exist
     */
    public String getRequiredRoomType() {
        return requiredRoomType != null ? requiredRoomType : department;
    }
    
    /**
     * Sets the required room type for this course.
     * 
     * @param requiredRoomType The type of room required, e.g., "LAB", "LECTURE_HALL", etc.
     */
    public void setRequiredRoomType(String requiredRoomType) {
        this.requiredRoomType = requiredRoomType;
    }
    
    /**
     * Gets the assigned lecturer ID from the course management system.
     * 
     * @return The ID of the lecturer assigned to this course, or null if none assigned
     */
    public String getAssignedLecturerId() {
        return assignedLecturerId;
    }
    
    /**
     * Sets the assigned lecturer ID.
     * 
     * @param assignedLecturerId The ID of the lecturer to assign to this course
     */
    public void setAssignedLecturerId(String assignedLecturerId) {
        this.assignedLecturerId = assignedLecturerId;
    }
    
    /**
     * Gets the assigned resource ID from the course management system.
     * 
     * @return The ID of the resource (room) assigned to this course, or null if none assigned
     */
    public String getAssignedResourceId() {
        return assignedResourceId;
    }
    
    /**
     * Sets the assigned resource ID.
     * 
     * @param assignedResourceId The ID of the resource (room) to assign to this course
     */
    public void setAssignedResourceId(String assignedResourceId) {
        this.assignedResourceId = assignedResourceId;
    }
    
    /**
     * Gets the name of the assigned lecturer.
     * 
     * @return The name of the lecturer assigned to this course, or null if none assigned
     */
    public String getLecturerName() {
        return lecturerName;
    }
    
    /**
     * Sets the name of the assigned lecturer.
     * 
     * @param lecturerName The name of the lecturer to assign to this course
     */
    public void setLecturerName(String lecturerName) {
        this.lecturerName = lecturerName;
    }
    
    /**
     * Gets the name or description of the assigned resource (room).
     * 
     * @return The name/description of the resource assigned to this course, or null if none assigned
     */
    public String getAssignedRoom() {
        return assignedRoom;
    }
    
    /**
     * Sets the name or description of the assigned resource (room).
     * 
     * @param assignedRoom The name/description of the resource assigned to this course
     */
    public void setAssignedRoom(String assignedRoom) {
        this.assignedRoom = assignedRoom;
    }
    
    // New getters and setters for the added fields
    
    public int getDurationHours() {
        return durationHours;
    }
    
    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }
    
    public int getTotalSessionsPerWeek() {
        return totalSessionsPerWeek;
    }
    
    public void setTotalSessionsPerWeek(int totalSessionsPerWeek) {
        this.totalSessionsPerWeek = totalSessionsPerWeek;
    }
    
    public boolean isSpreadCourseSessions() {
        return spreadCourseSessions;
    }
    
    public void setSpreadCourseSessions(boolean spreadCourseSessions) {
        this.spreadCourseSessions = spreadCourseSessions;
    }
    
    public int getNumberOfLectures() {
        return numberOfLectures;
    }
    
    public void setNumberOfLectures(int numberOfLectures) {
        this.numberOfLectures = numberOfLectures;
    }
    
    public String getAdminId() {
        return adminId;
    }
    
    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }
    
    public int getViewType() {
        return viewType;
    }
    
    public void setViewType(int viewType) {
        this.viewType = viewType;
    }
    
    public int getNumberOfLabs() {
        return numberOfLabs;
    }
    
    public void setNumberOfLabs(int numberOfLabs) {
        this.numberOfLabs = numberOfLabs;
    }
    
    /**
     * Calculates the typical duration for a session of this course
     * based on credit hours and required sessions per week
     * 
     * @return Duration in minutes
     */
    public int getTypicalSessionDuration() {
        // Simple formula: (credit hours * 60) / sessions per week
        // This is an example - adjust based on your academic regulations
        return (creditHours * 60) / requiredSessionsPerWeek;
    }
    
    /* 
     * Future constraint solver integration:
     * - Add preferred lecturers list
     * - Add required resource types (lab, lecture hall, etc.)
     * - Add student group associations
     * - Add prerequisites and corequisites
     */
}
