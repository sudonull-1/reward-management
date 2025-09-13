package com.rewardmanagement.reward_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing an available reward with remaining coins information.
 * Shows which rewards have coins available and when they expire (FIFO order).
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableRewardResponse {
    
    /**
     * The reward transaction ID.
     */
    private UUID rewardTransactionId;
    
    /**
     * Original number of coins in this reward.
     */
    private Integer originalCoins;
    
    /**
     * Number of coins remaining (not yet redeemed or expired).
     */
    private Integer remainingCoins;
    
    /**
     * Number of coins already redeemed from this reward.
     */
    private Integer redeemedCoins;
    
    /**
     * Number of coins already expired from this reward.
     */
    private Integer expiredCoins;
    
    /**
     * When this reward expires (null if no expiry).
     */
    private LocalDateTime expiresAt;
    
    /**
     * When this reward was created.
     */
    private LocalDateTime createdAt;
    
    /**
     * Whether this reward has expired.
     */
    private Boolean isExpired;
    
    /**
     * Priority order for FIFO processing (1 = first to be used).
     */
    private Integer fifoOrder;
}
