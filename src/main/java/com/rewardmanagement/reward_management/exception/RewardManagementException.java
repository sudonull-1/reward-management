package com.rewardmanagement.reward_management.exception;

/**
 * Base exception class for all reward management system exceptions.
 * Provides common functionality for error handling and logging.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public class RewardManagementException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * Constructs a new RewardManagementException with the specified detail message.
     * 
     * @param message The detail message
     */
    public RewardManagementException(String message) {
        super(message);
        this.errorCode = "REWARD_MANAGEMENT_ERROR";
    }
    
    /**
     * Constructs a new RewardManagementException with the specified detail message and error code.
     * 
     * @param message The detail message
     * @param errorCode The error code for categorizing the error
     */
    public RewardManagementException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new RewardManagementException with the specified detail message and cause.
     * 
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public RewardManagementException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "REWARD_MANAGEMENT_ERROR";
    }
    
    /**
     * Constructs a new RewardManagementException with the specified detail message, cause, and error code.
     * 
     * @param message The detail message
     * @param cause The cause of the exception
     * @param errorCode The error code for categorizing the error
     */
    public RewardManagementException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Gets the error code associated with this exception.
     * 
     * @return The error code
     */
    public String getErrorCode() {
        return errorCode;
    }
}
