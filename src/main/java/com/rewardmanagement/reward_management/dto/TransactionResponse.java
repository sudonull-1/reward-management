package com.rewardmanagement.reward_management.dto;

import com.rewardmanagement.reward_management.entity.TransactionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO representing a transaction in API responses.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    
    /**
     * Unique identifier for the transaction.
     */
    private UUID transactionId;
    
    /**
     * Type of transaction (REWARD, REDEEM, EXPIRY).
     */
    private TransactionType transactionType;
    
    /**
     * Number of coins involved in this transaction.
     */
    private Integer numberOfCoins;
    
    /**
     * Expiration timestamp for reward transactions (null for others).
     */
    private LocalDateTime expiresAt;
    
    /**
     * Timestamp when the transaction was created.
     */
    private LocalDateTime createdAt;
    
    /**
     * Balance impact of this transaction (positive for credit, negative for debit).
     */
    private Integer balanceImpact;
    
    /**
     * Whether this transaction has expired (applicable only to reward transactions).
     */
    private Boolean isExpired;
}
