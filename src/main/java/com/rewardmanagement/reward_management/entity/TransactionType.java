package com.rewardmanagement.reward_management.entity;

/**
 * Enumeration representing different types of transactions in the reward management system.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
public enum TransactionType {
    /**
     * Transaction type for crediting rewards to user's account
     */
    REWARD("Reward transaction - coins added"),
    
    /**
     * Transaction type for redeeming coins from user's account
     */
    REDEEM("Redeem transaction - coins deducted"),
    
    /**
     * Transaction type for expiring unused coins
     */
    EXPIRY("Expiry transaction - coins expired");
    
    private final String description;
    
    /**
     * Constructor for TransactionType enum.
     * 
     * @param description A human-readable description of the transaction type
     */
    TransactionType(String description) {
        this.description = description;
    }
    
    /**
     * Gets the description of the transaction type.
     * 
     * @return The description string
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if the transaction type involves adding coins to the user's balance.
     * 
     * @return true if this transaction type adds coins, false otherwise
     */
    public boolean isCredit() {
        return this == REWARD;
    }
    
    /**
     * Checks if the transaction type involves deducting coins from the user's balance.
     * 
     * @return true if this transaction type deducts coins, false otherwise
     */
    public boolean isDebit() {
        return this == REDEEM || this == EXPIRY;
    }
}
