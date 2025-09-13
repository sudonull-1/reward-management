package com.rewardmanagement.reward_management.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for all REST endpoints.
 * Provides consistent response format across the application.
 * 
 * @param <T> Type of the response data
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    /**
     * Indicates whether the API call was successful.
     */
    private boolean success;
    
    /**
     * Human-readable message describing the result.
     */
    private String message;
    
    /**
     * The actual response data. Null in case of errors.
     */
    private T data;
    
    /**
     * Error code (if applicable). Null for successful responses.
     */
    private String errorCode;
    
    /**
     * Timestamp when the response was generated.
     */
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Creates a successful response with data.
     * 
     * @param data The response data
     * @param message Success message
     * @param <T> Type of the response data
     * @return ApiResponse with success = true
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        response.setErrorCode(null);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    /**
     * Creates a successful response with default message.
     * 
     * @param data The response data
     * @param <T> Type of the response data
     * @return ApiResponse with success = true
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Operation completed successfully");
    }
    
    /**
     * Creates an error response.
     * 
     * @param message Error message
     * @param errorCode Error code
     * @param <T> Type of the response data
     * @return ApiResponse with success = false
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setData(null);
        response.setErrorCode(errorCode);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    /**
     * Creates an error response with default error code.
     * 
     * @param message Error message
     * @param <T> Type of the response data
     * @return ApiResponse with success = false
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(message, "GENERAL_ERROR");
    }
}
