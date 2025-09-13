package com.rewardmanagement.reward_management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a user in the reward management system.
 * Contains user information and their current coin balance.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_updated_at", columnList = "updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    
    /**
     * Unique identifier for the user.
     */
    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    @NotBlank(message = "User ID cannot be blank")
    @Size(max = 255, message = "User ID must not exceed 255 characters")
    @EqualsAndHashCode.Include
    private String userId;
    
    /**
     * Current coin balance for the user.
     * Must be non-negative.
     */
    @Column(name = "coins", nullable = false)
    @Min(value = 0, message = "Coins balance cannot be negative")
    private Integer coins = 0;
    
    /**
     * List of transactions associated with this user.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();
    
    /**
     * Timestamp when the user was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the user was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Adds coins to the user's balance.
     * This method ensures the balance doesn't become negative and creates an audit trail.
     * 
     * @param coinsToAdd Number of coins to add (must be positive)
     * @throws IllegalArgumentException if coinsToAdd is not positive
     */
    public void addCoins(@Positive int coinsToAdd) {
        if (coinsToAdd <= 0) {
            throw new IllegalArgumentException("Coins to add must be positive");
        }
        this.coins = this.coins + coinsToAdd;
    }
    
    /**
     * Removes coins from the user's balance.
     * This method ensures the balance doesn't become negative.
     * 
     * @param coinsToRemove Number of coins to remove (must be positive)
     * @throws IllegalArgumentException if coinsToRemove is not positive or if insufficient balance
     */
    public void removeCoins(@Positive int coinsToRemove) {
        if (coinsToRemove <= 0) {
            throw new IllegalArgumentException("Coins to remove must be positive");
        }
        if (this.coins < coinsToRemove) {
            throw new IllegalArgumentException("Insufficient balance. Current balance: " + this.coins + ", Requested: " + coinsToRemove);
        }
        this.coins = this.coins - coinsToRemove;
    }
    
    /**
     * Gets the current coin balance.
     * 
     * @return Current coin balance
     */
    public Integer viewCoins() {
        return this.coins;
    }
    
    /**
     * Checks if the user has sufficient balance for a given amount.
     * 
     * @param requiredAmount Amount to check
     * @return true if user has sufficient balance, false otherwise
     */
    public boolean hasSufficientBalance(int requiredAmount) {
        return this.coins >= requiredAmount;
    }
    
    /**
     * Pre-persist method to set creation timestamp if not already set.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
    }
    
    /**
     * Pre-update method to update the last modified timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
