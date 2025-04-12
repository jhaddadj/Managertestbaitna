package com.example.manager.model;

/**
 * Lecturer model class that represents a lecturer in the educational system.
 * This class stores essential information about lecturers that is used for 
 * scheduling and display purposes throughout the application.
 */
public class Lecturer {
    private final String id;          // Unique identifier for the lecturer
    private final String name;        // Full name of the lecturer
    private final String contact;     // Contact information (likely phone or email)
    private final int proximityScore; // Score used for optimizing lecturer assignments based on proximity to classrooms
    private final String department;  // Primary department of the lecturer

    /**
     * Constructor to create a new Lecturer object with all required properties.
     * 
     * @param id The unique identifier for the lecturer
     * @param name The lecturer's full name
     * @param contact The lecturer's contact information
     * @param proximityScore A score representing the lecturer's proximity preference for scheduling
     * @param department The primary department the lecturer belongs to
     */
    public Lecturer(String id, String name, String contact, int proximityScore, String department) {
        this.id = id;
        this.name = name;
        this.contact = contact;
        this.proximityScore = proximityScore;
        this.department = department;
    }

    /**
     * Returns the lecturer's unique identifier.
     * 
     * @return String containing the lecturer's ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the lecturer's full name.
     * 
     * @return String containing the lecturer's name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the lecturer's contact information.
     * 
     * @return String containing the lecturer's contact details
     */
    public String getContact() {
        return contact;
    }

    /**
     * Returns the lecturer's proximity score used for schedule optimization.
     * Higher scores might indicate preference for specific locations or buildings.
     * 
     * @return Integer representing the proximity score
     */
    public int getProximityScore() {
        return proximityScore;
    }

    /**
     * Returns the primary department the lecturer belongs to.
     * 
     * @return String containing the department name
     */
    public String getDepartment() {
        return department;
    }
}
