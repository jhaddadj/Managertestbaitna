package com.example.manager.model;

/**
 * User model class that represents any user in the educational system.
 * This class serves as the primary user model containing common attributes
 * shared by administrators, lecturers, and students.
 * It's used for user management, authentication, and display throughout the application.
 */
public class User {
    private String id;        // Unique identifier for the user
    private String name;      // Full name of the user
    private String email;     // Email address used for authentication and communication
    private String role;      // User role (admin, lecturer, student)
    private String contact;   // Contact number
    private String idPhoto;   // URL or reference to the user's ID photo
    private String contract;  // URL or reference to the user's contract document
    private String status;    // Account status (e.g., pending, approved, rejected)

    /**
     * Default constructor required for Firebase database operations.
     */
    public User() {
    }

    /**
     * Constructor to create a new User object with all required properties.
     * 
     * @param id Unique identifier for the user
     * @param name Full name of the user
     * @param email Email address of the user
     * @param contact Contact number of the user
     * @param role User's role in the system
     * @param idPhoto Reference to the user's ID photo
     * @param contract Reference to the user's contract
     * @param status Account status
     */
    public User(String id, String name, String email,String contact, String role, String idPhoto, String contract, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.contact=contact;
        this.role = role;
        this.idPhoto = idPhoto;
        this.contract = contract;
        this.status = status;
    }

    /**
     * Returns the user's contact number.
     * 
     * @return String containing the user's contact number
     */
    public String getContact() {
        return contact;
    }

    /**
     * Sets the user's contact number.
     * 
     * @param contact New contact number
     */
    public void setContact(String contact) {
        this.contact = contact;
    }

    /**
     * Returns the user's full name.
     * 
     * @return String containing the user's name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's full name.
     * 
     * @param name New name for the user
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the user's email address.
     * 
     * @return String containing the user's email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     * 
     * @param email New email for the user
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the user's role in the system.
     * 
     * @return String representing the user's role (admin, lecturer, student)
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the user's role in the system.
     * 
     * @param role New role for the user
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns the reference to the user's ID photo.
     * 
     * @return String containing URL or path to the ID photo
     */
    public String getIdPhoto() {
        return idPhoto;
    }

    /**
     * Sets the reference to the user's ID photo.
     * 
     * @param idPhoto New reference to ID photo
     */
    public void setIdPhoto(String idPhoto) {
        this.idPhoto = idPhoto;
    }

    /**
     * Returns the reference to the user's contract document.
     * 
     * @return String containing URL or path to the contract
     */
    public String getContract() {
        return contract;
    }

    /**
     * Sets the reference to the user's contract document.
     * 
     * @param contract New reference to contract
     */
    public void setContract(String contract) {
        this.contract = contract;
    }

    /**
     * Returns the user's account status.
     * 
     * @return String representing the account status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the user's account status.
     * 
     * @param status New account status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the user's unique identifier.
     * 
     * @return String containing the user's ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the user's unique identifier.
     * 
     * @param id New ID for the user
     */
    public void setId(String id) {
        this.id = id;
    }
}
