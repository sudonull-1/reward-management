package com.rewardmanagement.reward_management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for viewing user's coin balance and transaction history.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewResult {
    
    /**
     * User ID for which the result is generated.
     */
    private String userId;
    
    /**
     * Current total number of coins in user's account.
     */
    private Integer totalCoins;
    
    /**
     * List of transactions for the user, ordered by creation date (most recent first).
     */
    private List<TransactionResponse> transactions;
    
    /**
     * Number of coins that will expire within the next 30 minutes.
     */
    private Integer coinsExpiringIn30Mins;
    
    /**
     * Number of active reward transactions (not expired).
     */
    private Integer activeRewardTransactions;
    
    /**
     * Timestamp when this result was generated.
     */
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    /**
     * Available rewards ordered by FIFO (expiring first).
     * Shows detailed breakdown of each reward's remaining coins.
     */
    private List<AvailableRewardResponse> availableRewards;
}
