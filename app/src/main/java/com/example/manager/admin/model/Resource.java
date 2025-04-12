package com.example.manager.admin.model;

/**
 * The Resource class represents educational resources managed by administrators in the FinalManager app.
 * These resources can include classrooms, labs, equipment, and other facilities that are used
 * for educational purposes within the institution.
 * 
 * Each resource is characterized by a unique identifier, name, type, capacity, location,
 * availability status, and the ID of the administrator who manages it.
 */
public class Resource {
    private String id;          // Unique identifier for the resource
    private String name;        // Name of the resource (e.g., "Lab 101", "Smart Board 3")
    private String type;        // Type of resource (e.g., "Classroom", "Laboratory", "Equipment")
    private String capacity;    // Capacity of the resource (e.g., number of seats or users)
    private String adminId;     // ID of the administrator managing this resource
    private String location;    // Physical location of the resource within the institution
    private String isAvailable; // Availability status ("yes" or "no")

    /**
     * Default constructor required for Firebase data deserialization.
     */
    public Resource() {
    }

    /**
     * Constructs a fully initialized Resource object with all required properties.
     *
     * @param id Unique identifier for the resource
     * @param name Name of the resource
     * @param type Type of resource (e.g., classroom, laboratory, equipment)
     * @param capacity Capacity or size of the resource
     * @param adminId ID of the administrator managing this resource
     * @param location Physical location of the resource
     * @param isAvailable Availability status ("yes" for available, "no" for unavailable)
     */
    public Resource(String id, String name, String type, String capacity, String adminId, String location, String isAvailable) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
        this.adminId = adminId;
        this.location = location;
        this.isAvailable = isAvailable;
    }

    /**
     * Gets the unique identifier of the resource.
     *
     * @return The resource ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the resource.
     *
     * @param id The resource ID to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the name of the resource.
     *
     * @return The resource name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the resource.
     *
     * @param name The resource name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the type of the resource.
     *
     * @return The resource type (e.g., classroom, laboratory, equipment)
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the resource.
     *
     * @param type The resource type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the administrator ID associated with this resource.
     *
     * @return The admin ID
     */
    public String getAdminId() {
        return adminId;
    }

    /**
     * Sets the administrator ID associated with this resource.
     *
     * @param adminId The admin ID to set
     */
    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    /**
     * Gets the capacity of the resource.
     *
     * @return The resource capacity (e.g., number of seats or users)
     */
    public String getCapacity() {
        return capacity;
    }

    /**
     * Sets the capacity of the resource.
     *
     * @param capacity The resource capacity to set
     */
    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    /**
     * Gets the availability status of the resource.
     *
     * @return "yes" if the resource is available, "no" if not available
     */
    public String getIsAvailable() {
        return isAvailable;
    }

    /**
     * Sets the availability status of the resource.
     *
     * @param isAvailable "yes" to mark as available, "no" to mark as unavailable
     */
    public void setIsAvailable(String isAvailable) {
        this.isAvailable = isAvailable;
    }

    /**
     * Gets the physical location of the resource.
     *
     * @return The location of the resource within the institution
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the physical location of the resource.
     *
     * @param location The location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }
}
