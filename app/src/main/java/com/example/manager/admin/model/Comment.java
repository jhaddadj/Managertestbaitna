package com.example.manager.admin.model;

public class Comment {
    private String commentId;
    private String lecturerId;
    private String lecturerName;
    private String commentText;
    private String timestamp;

    public Comment() {
    }

    public Comment(String commentId, String lecturerId,String lecturerName, String commentText, String timestamp) {
        this.commentId = commentId;
        this.lecturerId = lecturerId;
        this.lecturerName=lecturerName;
        this.commentText = commentText;
        this.timestamp = timestamp;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
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

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
