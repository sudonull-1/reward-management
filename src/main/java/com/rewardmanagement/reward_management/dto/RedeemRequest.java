package com.rewardmanagement.reward_management.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for redeeming coins from a user's account.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemRequest {
    
    /**
     * Number of coins to be redeemed from the user's account.
     * Must be positive.
     */
    @NotNull(message = "Number of coins is required")
    @Positive(message = "Number of coins must be positive")
    @Max(value = 10000, message = "Number of coins cannot exceed 10000 per transaction")
    private Integer numberOfCoins;
}
