package com.rewardmanagement.reward_management.exception;

/**
 * Exception thrown when an invalid transaction is attempted.
 * This includes business rule violations and invalid transaction parameters.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public class InvalidTransactionException extends RewardManagementException {
    
    /**
     * Constructs a new InvalidTransactionException with a default message.
     * 
     * @param message The detail message explaining why the transaction is invalid
     */
    public InvalidTransactionException(String message) {
        super(message, "INVALID_TRANSACTION");
    }
    
    /**
     * Constructs a new InvalidTransactionException with a custom error code.
     * 
     * @param message The detail message explaining why the transaction is invalid
     * @param errorCode Custom error code for the specific type of invalid transaction
     */
    public InvalidTransactionException(String message, String errorCode) {
        super(message, errorCode);
    }
    
    /**
     * Constructs a new InvalidTransactionException with a cause.
     * 
     * @param message The detail message explaining why the transaction is invalid
     * @param cause The underlying cause of the invalid transaction
     */
    public InvalidTransactionException(String message, Throwable cause) {
        super(message, cause, "INVALID_TRANSACTION");
    }
}
