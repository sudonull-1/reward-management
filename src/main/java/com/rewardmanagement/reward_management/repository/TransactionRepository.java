package com.rewardmanagement.reward_management.repository;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Transaction entity operations.
 * Provides CRUD operations and custom queries for transaction management.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * Finds all transactions for a specific user, ordered by creation date (most recent first).
     * 
     * @param userId The user ID to search for
     * @return List of transactions for the user
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);
    
    /**
     * Finds transactions by user ID and transaction type.
     * 
     * @param userId The user ID
     * @param transactionType The transaction type to filter by
     * @return List of matching transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = :type ORDER BY t.createdAt DESC")
    List<Transaction> findByUserIdAndTransactionType(@Param("userId") String userId, 
                                                    @Param("type") TransactionType transactionType);
    
    /**
     * Finds all reward transactions that will expire within the specified time frame.
     * 
     * @param expiryTime The timestamp to check against
     * @return List of transactions expiring before the specified time
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionType = 'REWARD' AND t.expiresAt <= :expiryTime ORDER BY t.expiresAt ASC")
    List<Transaction> findExpiringRewardTransactions(@Param("expiryTime") LocalDateTime expiryTime);
    
    /**
     * Finds active reward transactions for a user that will expire within specified minutes.
     * Only includes rewards that haven't been processed for expiry yet.
     * 
     * @param userId The user ID
     * @param expiryTime The expiry timestamp to check against
     * @param currentTime Current timestamp to exclude already expired rewards
     * @return List of active reward transactions expiring before the specified time
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = 'REWARD' " +
           "AND t.expiresAt <= :expiryTime AND t.expiresAt > :currentTime " +
           "AND NOT EXISTS (SELECT e FROM Transaction e WHERE e.user.userId = :userId AND e.transactionType = 'EXPIRY' " +
           "AND e.numberOfCoins = t.numberOfCoins AND e.createdAt > t.createdAt) " +
           "ORDER BY t.expiresAt ASC")
    List<Transaction> findUserRewardsExpiringBefore(@Param("userId") String userId, 
                                                   @Param("expiryTime") LocalDateTime expiryTime,
                                                   @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Calculates the total coins for a user from active (non-expired) reward transactions.
     * 
     * @param userId The user ID
     * @param currentTime Current timestamp to determine expiry
     * @return Sum of coins from active reward transactions
     */
    @Query("SELECT COALESCE(SUM(t.numberOfCoins), 0) FROM Transaction t " +
           "WHERE t.user.userId = :userId AND t.transactionType = 'REWARD' " +
           "AND (t.expiresAt IS NULL OR t.expiresAt > :currentTime)")
    Integer sumActiveRewardCoins(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Calculates the total coins redeemed by a user.
     * 
     * @param userId The user ID
     * @return Sum of coins from redeem transactions
     */
    @Query("SELECT COALESCE(SUM(t.numberOfCoins), 0) FROM Transaction t " +
           "WHERE t.user.userId = :userId AND t.transactionType = 'REDEEM'")
    Integer sumRedeemedCoins(@Param("userId") String userId);
    
    /**
     * Calculates the total expired coins for a user.
     * 
     * @param userId The user ID
     * @return Sum of coins from expiry transactions
     */
    @Query("SELECT COALESCE(SUM(t.numberOfCoins), 0) FROM Transaction t " +
           "WHERE t.user.userId = :userId AND t.transactionType = 'EXPIRY'")
    Integer sumExpiredCoins(@Param("userId") String userId);
    
    /**
     * Finds recent transactions within a time range.
     * 
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return List of transactions within the specified time range
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :startTime AND :endTime ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsBetween(@Param("startTime") LocalDateTime startTime, 
                                             @Param("endTime") LocalDateTime endTime);
    
    /**
     * Counts transactions by type for a specific user.
     * 
     * @param userId The user ID
     * @param transactionType The transaction type
     * @return Count of transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = :type")
    long countByUserIdAndTransactionType(@Param("userId") String userId, 
                                        @Param("type") TransactionType transactionType);
    
    /**
     * Finds the most recent transaction for a user.
     * 
     * @param userId The user ID
     * @return List containing the most recent transaction (may be empty)
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findLatestTransactionByUserId(@Param("userId") String userId);
    
    /**
     * Finds all active reward transactions (not expired) for a user.
     * 
     * @param userId The user ID
     * @param currentTime Current timestamp
     * @return List of active reward transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = 'REWARD' " +
           "AND (t.expiresAt IS NULL OR t.expiresAt > :currentTime) ORDER BY t.expiresAt ASC")
    List<Transaction> findActiveRewardTransactions(@Param("userId") String userId, 
                                                  @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Finds all expired reward transactions for cleanup purposes.
     * 
     * @param currentTime Current timestamp
     * @return List of expired reward transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionType = 'REWARD' AND t.expiresAt <= :currentTime")
    List<Transaction> findExpiredRewardTransactions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Finds expired reward transactions for a specific user that haven't been processed yet.
     * Uses a more precise approach to avoid duplicate processing.
     * 
     * @param userId The user ID to find expired rewards for
     * @param currentTime Current timestamp to compare against expiry time
     * @return List of expired reward transactions for the user that need processing
     */
    @Query("SELECT DISTINCT t FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = 'REWARD' " +
           "AND t.expiresAt <= :currentTime " +
           "ORDER BY t.expiresAt ASC")
    List<Transaction> findExpiredRewardTransactionsByUser(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Finds available reward transactions for a user ordered by expiry date (FIFO).
     * Returns rewards that haven't been fully redeemed or expired yet.
     * 
     * @param userId The user ID
     * @param currentTime Current timestamp
     * @return List of available reward transactions ordered by expiry date (earliest first)
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.userId = :userId AND t.transactionType = 'REWARD' " +
           "AND (t.expiresAt IS NULL OR t.expiresAt > :currentTime) " +
           "ORDER BY t.expiresAt ASC NULLS LAST")
    List<Transaction> findAvailableRewardTransactionsByUser(@Param("userId") String userId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Calculates the remaining coins for a specific reward transaction.
     * This considers all REDEEM and EXPIRY transactions that reference this reward.
     * 
     * @param rewardTransactionId The reward transaction ID
     * @return The remaining coins available from this reward
     */
    @Query("SELECT COALESCE(:originalCoins - SUM(t.numberOfCoins), :originalCoins) " +
           "FROM Transaction t WHERE t.sourceReward.transactionId = :rewardTransactionId " +
           "AND t.transactionType IN ('REDEEM', 'EXPIRY')")
    Integer calculateRemainingCoins(@Param("rewardTransactionId") UUID rewardTransactionId, @Param("originalCoins") Integer originalCoins);
    
    /**
     * Finds all REDEEM and EXPIRY transactions for a specific reward transaction.
     * 
     * @param rewardTransactionId The reward transaction ID
     * @return List of transactions that consumed coins from this reward
     */
    @Query("SELECT t FROM Transaction t WHERE t.sourceReward.transactionId = :rewardTransactionId " +
           "AND t.transactionType IN ('REDEEM', 'EXPIRY') ORDER BY t.createdAt ASC")
    List<Transaction> findConsumptionTransactionsByReward(@Param("rewardTransactionId") UUID rewardTransactionId);
}
