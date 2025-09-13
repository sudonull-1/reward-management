package com.rewardmanagement.reward_management.exception;

/**
 * Exception thrown when a user attempts to redeem more coins than their available balance.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public class InsufficientBalanceException extends RewardManagementException {
    
    private final String userId;
    private final Integer currentBalance;
    private final Integer requestedAmount;
    
    /**
     * Constructs a new InsufficientBalanceException.
     * 
     * @param userId The user ID attempting the transaction
     * @param currentBalance The user's current coin balance
     * @param requestedAmount The amount requested for redemption
     */
    public InsufficientBalanceException(String userId, Integer currentBalance, Integer requestedAmount) {
        super(String.format("Insufficient balance for user %s. Current balance: %d, Requested: %d", 
              userId, currentBalance, requestedAmount), "INSUFFICIENT_BALANCE");
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
    
    /**
     * Gets the user ID associated with this exception.
     * 
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Gets the current balance at the time of the exception.
     * 
     * @return The current balance
     */
    public Integer getCurrentBalance() {
        return currentBalance;
    }
    
    /**
     * Gets the requested amount that caused the exception.
     * 
     * @return The requested amount
     */
    public Integer getRequestedAmount() {
        return requestedAmount;
    }
}
