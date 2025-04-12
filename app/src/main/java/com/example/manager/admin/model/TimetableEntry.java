package com.example.manager.admin.model;

import java.util.List;

public class TimetableEntry {
    private String courseId,adminId, courseName, lecturerId, lecturerName ,roomId, roomName, timeSlot,location,lecContact,startDate,endDate;

    private List<String> day;

    public TimetableEntry() {
    }

    public TimetableEntry(String courseId, String adminId, String courseName, String lecturerId, String lecturerName, String roomId, String roomName, List<String> day, String timeSlot,String location,String lecContact,String startDate, String endDate) {
        this.courseId = courseId;
        this.adminId= adminId;
        this.courseName = courseName;
        this.lecturerId = lecturerId;
        this.lecturerName = lecturerName;
        this.roomId = roomId;
        this.roomName = roomName;
        this.day = day;
        this.timeSlot = timeSlot;
        this.location=location;
        this.lecContact=lecContact;
        this.startDate=startDate;
        this.endDate=endDate;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
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

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public List<String> getDay() {
        return day;
    }

    public void setDay(List<String> day) {
        this.day = day;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLecContact() {
        return lecContact;
    }

    public void setLecContact(String lecContact) {
        this.lecContact = lecContact;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
