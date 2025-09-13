package com.rewardmanagement.reward_management.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested transaction is not found in the system.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public class TransactionNotFoundException extends RewardManagementException {
    
    /**
     * Constructs a new TransactionNotFoundException with a default message.
     * 
     * @param transactionId The transaction ID that was not found
     */
    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found with ID: " + transactionId, "TRANSACTION_NOT_FOUND");
    }
    
    /**
     * Constructs a new TransactionNotFoundException with a custom message.
     * 
     * @param transactionId The transaction ID that was not found
     * @param customMessage Custom error message
     */
    public TransactionNotFoundException(UUID transactionId, String customMessage) {
        super(customMessage + " (Transaction ID: " + transactionId + ")", "TRANSACTION_NOT_FOUND");
    }
}
