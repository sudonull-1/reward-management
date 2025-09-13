package com.rewardmanagement.reward_management.repository;

import com.rewardmanagement.reward_management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations.
 * Provides CRUD operations and custom queries for user management.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * Finds a user by user ID with optimistic locking.
     * 
     * @param userId The user ID to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByUserId(String userId);
    
    /**
     * Finds users with coins greater than the specified amount.
     * 
     * @param minCoins Minimum number of coins
     * @return List of users with coins >= minCoins
     */
    @Query("SELECT u FROM User u WHERE u.coins >= :minCoins ORDER BY u.coins DESC")
    List<User> findUsersWithMinimumCoins(@Param("minCoins") Integer minCoins);
    
    /**
     * Finds users who have been updated recently.
     * 
     * @param since Timestamp to search from
     * @return List of users updated after the specified timestamp
     */
    @Query("SELECT u FROM User u WHERE u.updatedAt >= :since ORDER BY u.updatedAt DESC")
    List<User> findRecentlyUpdatedUsers(@Param("since") LocalDateTime since);
    
    /**
     * Updates the coin balance for a specific user.
     * This method provides optimistic performance for balance updates.
     * 
     * @param userId The user ID
     * @param newBalance The new coin balance
     * @return Number of rows affected (should be 1 if successful)
     */
    @Modifying
    @Query("UPDATE User u SET u.coins = :newBalance, u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int updateUserBalance(@Param("userId") String userId, @Param("newBalance") Integer newBalance);
    
    /**
     * Increments the coin balance for a specific user atomically.
     * 
     * @param userId The user ID
     * @param increment Amount to add to current balance
     * @return Number of rows affected (should be 1 if successful)
     */
    @Modifying
    @Query("UPDATE User u SET u.coins = u.coins + :increment, u.updatedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int incrementUserBalance(@Param("userId") String userId, @Param("increment") Integer increment);
    
    /**
     * Decrements the coin balance for a specific user atomically, only if sufficient balance exists.
     * 
     * @param userId The user ID
     * @param decrement Amount to subtract from current balance
     * @param minRequired Minimum balance required for the operation to proceed
     * @return Number of rows affected (1 if successful, 0 if insufficient balance)
     */
    @Modifying
    @Query("UPDATE User u SET u.coins = u.coins - :decrement, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId AND u.coins >= :minRequired")
    int decrementUserBalanceWithCheck(@Param("userId") String userId, 
                                     @Param("decrement") Integer decrement, 
                                     @Param("minRequired") Integer minRequired);
    
    /**
     * Checks if a user exists with the given ID.
     * 
     * @param userId The user ID to check
     * @return true if user exists, false otherwise
     */
    boolean existsByUserId(String userId);
    
    /**
     * Gets the current coin balance for a user calculated from transactions.
     * This ensures the balance is always accurate based on transaction history.
     * 
     * @param userId The user ID
     * @return Optional containing the calculated coin balance if user exists
     */
    @Query("SELECT COALESCE(SUM(CASE " +
           "WHEN t.transactionType = 'REWARD' THEN t.numberOfCoins " +
           "WHEN t.transactionType = 'REDEEM' THEN -t.numberOfCoins " +
           "WHEN t.transactionType = 'EXPIRY' THEN -t.numberOfCoins " +
           "ELSE 0 END), 0) " +
           "FROM User u LEFT JOIN u.transactions t WHERE u.userId = :userId")
    Optional<Integer> getUserBalance(@Param("userId") String userId);
    
    /**
     * Counts total number of users in the system.
     * 
     * @return Total count of users
     */
    @Query("SELECT COUNT(u) FROM User u")
    long countTotalUsers();
    
    /**
     * Finds users with zero balance.
     * 
     * @return List of users with no coins
     */
    @Query("SELECT u FROM User u WHERE u.coins = 0 ORDER BY u.updatedAt DESC")
    List<User> findUsersWithZeroBalance();
}
