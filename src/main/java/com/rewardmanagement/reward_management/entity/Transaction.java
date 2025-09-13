package com.rewardmanagement.reward_management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a transaction in the reward management system.
 * Each transaction records a change in a user's coin balance.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_user_id", columnList = "user_id"),
    @Index(name = "idx_transactions_expires_at", columnList = "expires_at"),
    @Index(name = "idx_transactions_type_created", columnList = "transaction_type, created_at")
})
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {
    
    /**
     * Unique identifier for the transaction.
     */
    @Id
    @UuidGenerator
    @Column(name = "transaction_id", nullable = false)
    @EqualsAndHashCode.Include
    private UUID transactionId;
    
    /**
     * Reference to the user who owns this transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transaction_user"))
    @NotNull(message = "User cannot be null")
    @ToString.Exclude
    private User user;
    
    /**
     * Type of transaction (REWARD, REDEEM, EXPIRY).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    @NotNull(message = "Transaction type cannot be null")
    private TransactionType transactionType;
    
    /**
     * Number of coins involved in this transaction.
     * Must be positive for all transaction types.
     */
    @Column(name = "number_of_coins", nullable = false)
    @Positive(message = "Number of coins must be positive")
    private Integer numberOfCoins;
    
    /**
     * Expiration timestamp for reward transactions.
     * Only applicable for REWARD transactions.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    /**
     * Timestamp when the transaction was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the transaction was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Creates a new reward transaction.
     * Reward transactions have an expiration date.
     * 
     * @param user The user receiving the reward
     * @param numberOfCoins Number of coins to reward
     * @param expiresAt Expiration timestamp for the reward
     * @return New Transaction instance
     */
    public static Transaction createRewardTransaction(User user, int numberOfCoins, LocalDateTime expiresAt) {
        if (numberOfCoins <= 0) {
            throw new IllegalArgumentException("Number of coins must be positive");
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expiration date cannot be in the past");
        }
        
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType(TransactionType.REWARD);
        transaction.setNumberOfCoins(numberOfCoins);
        transaction.setExpiresAt(expiresAt);
        return transaction;
    }
    
    /**
     * Creates a new redeem transaction.
     * Redeem transactions don't have expiration dates.
     * 
     * @param user The user redeeming coins
     * @param numberOfCoins Number of coins to redeem
     * @return New Transaction instance
     */
    public static Transaction createRedeemTransaction(User user, int numberOfCoins) {
        if (numberOfCoins <= 0) {
            throw new IllegalArgumentException("Number of coins must be positive");
        }
        
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType(TransactionType.REDEEM);
        transaction.setNumberOfCoins(numberOfCoins);
        transaction.setExpiresAt(null); // Redeem transactions don't expire
        return transaction;
    }
    
    /**
     * Creates a new expiry transaction.
     * Expiry transactions are created when reward coins expire.
     * 
     * @param user The user whose coins are expiring
     * @param numberOfCoins Number of coins expiring
     * @return New Transaction instance
     */
    public static Transaction createExpiryTransaction(User user, int numberOfCoins) {
        if (numberOfCoins <= 0) {
            throw new IllegalArgumentException("Number of coins must be positive");
        }
        
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType(TransactionType.EXPIRY);
        transaction.setNumberOfCoins(numberOfCoins);
        transaction.setExpiresAt(null); // Expiry transactions don't have expiration
        return transaction;
    }
    
    /**
     * Checks if this transaction has expired.
     * 
     * @return true if transaction has expiration date and it's in the past
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * Checks if this transaction will expire within the given minutes.
     * 
     * @param minutes Number of minutes to check ahead
     * @return true if transaction will expire within the specified minutes
     */
    public boolean isExpiringWithin(int minutes) {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isBefore(LocalDateTime.now().plusMinutes(minutes));
    }
    
    /**
     * Gets the impact of this transaction on the user's balance.
     * Positive for credit transactions, negative for debit transactions.
     * 
     * @return The balance impact (positive for credit, negative for debit)
     */
    public int getBalanceImpact() {
        if (transactionType.isCredit()) {
            return numberOfCoins;
        } else if (transactionType.isDebit()) {
            return -numberOfCoins;
        }
        return 0;
    }
    
    /**
     * Pre-persist method to set creation timestamp and validate business rules.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        validateTransaction();
    }
    
    /**
     * Pre-update method to update the last modified timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        validateTransaction();
    }
    
    /**
     * Validates the transaction according to business rules.
     */
    private void validateTransaction() {
        // Reward transactions should have expiration dates
        if (transactionType == TransactionType.REWARD && expiresAt == null) {
            throw new IllegalStateException("Reward transactions must have an expiration date");
        }
        
        // Non-reward transactions should not have expiration dates
        if (transactionType != TransactionType.REWARD && expiresAt != null) {
            throw new IllegalStateException("Only reward transactions can have expiration dates");
        }
        
        // Expiration date should be in the future for new reward transactions
        if (transactionType == TransactionType.REWARD && expiresAt != null && 
            expiresAt.isBefore(LocalDateTime.now()) && transactionId == null) {
            throw new IllegalStateException("Expiration date cannot be in the past for new transactions");
        }
    }
}
