package com.rewardmanagement.reward_management.exception;

/**
 * Exception thrown when a requested user is not found in the system.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public class UserNotFoundException extends RewardManagementException {
    
    /**
     * Constructs a new UserNotFoundException with a default message.
     * 
     * @param userId The user ID that was not found
     */
    public UserNotFoundException(String userId) {
        super("User not found with ID: " + userId, "USER_NOT_FOUND");
    }
    
    /**
     * Constructs a new UserNotFoundException with a custom message.
     * 
     * @param userId The user ID that was not found
     * @param customMessage Custom error message
     */
    public UserNotFoundException(String userId, String customMessage) {
        super(customMessage + " (User ID: " + userId + ")", "USER_NOT_FOUND");
    }
}
