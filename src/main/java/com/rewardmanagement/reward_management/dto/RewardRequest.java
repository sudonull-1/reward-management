package com.rewardmanagement.reward_management.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for crediting rewards to a user's account.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardRequest {
    
    /**
     * Number of coins to be credited to the user's account.
     * Must be positive.
     */
    @NotNull(message = "Number of coins is required")
    @Positive(message = "Number of coins must be positive")
    @Max(value = 10000, message = "Number of coins cannot exceed 10000 per transaction")
    private Integer numberOfCoins;
    
    /**
     * Optional expiration duration in minutes from now.
     * If not provided, defaults to 60 minutes (1 hour).
     */
    @Min(value = 1, message = "Expiration minutes must be at least 1")
    @Max(value = 525600, message = "Expiration minutes cannot exceed 525600 (1 year)")
    private Integer expirationMinutes = 60;
}
