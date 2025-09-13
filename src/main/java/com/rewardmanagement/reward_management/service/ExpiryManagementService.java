package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.exception.RewardManagementException;
import com.rewardmanagement.reward_management.exception.UserNotFoundException;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import com.rewardmanagement.reward_management.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.rewardmanagement.reward_management.entity.TransactionType;

/**
 * Service responsible for managing coin expiry operations.
 * Handles real-time expiry processing and maintains expiry cache.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Service
@Slf4j
public class ExpiryManagementService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String EXPIRY_CACHE_PREFIX = "reward:expiry:";
    private static final String EXPIRY_STATS_KEY = "reward:expiry:stats";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor for dependency injection.
     * 
     * @param transactionRepository Repository for transaction operations
     * @param userRepository Repository for user operations
     * @param redisTemplate Redis template for cache operations
     */
    @Autowired
    public ExpiryManagementService(TransactionRepository transactionRepository, 
                                 UserRepository userRepository,
                                 RedisTemplate<String, Object> redisTemplate) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Scheduled task that runs every 10 minutes to update Redis cache with information
     * about coins that will expire in the next 30 minutes.
     * 
     * This aligns with the HLD specification that mentions a cron job running every 10 minutes.
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 10 minutes in milliseconds
    public void updateExpiryCache() {
        log.info("Starting expiry cache update task");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime thirtyMinutesFromNow = now.plusMinutes(30);
            
            // Get all reward transactions expiring in the next 30 minutes
            List<Transaction> expiringTransactions = transactionRepository
                    .findExpiringRewardTransactions(thirtyMinutesFromNow);
            
            // Group by user ID and sum coins
            Map<String, Integer> userExpiringCoins = expiringTransactions.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getUser().getUserId(),
                            Collectors.summingInt(Transaction::getNumberOfCoins)
                    ));
            
            // Update Redis cache for each user
            userExpiringCoins.forEach((userId, coins) -> {
                String cacheKey = EXPIRY_CACHE_PREFIX + userId;
                
                Map<String, Object> expiryData = new HashMap<>();
                expiryData.put("userId", userId);
                expiryData.put("coinsExpiringIn30Min", coins);
                expiryData.put("lastUpdated", now.format(DATE_FORMATTER));
                
                // Set with 15-minute expiration (refresh before next scheduled run)
                redisTemplate.opsForValue().set(cacheKey, expiryData, 15, TimeUnit.MINUTES);
                
                log.debug("Updated expiry cache for user {}: {} coins expiring in 30 minutes", 
                        userId, coins);
            });
            
            // Update general statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsersWithExpiringCoins", userExpiringCoins.size());
            stats.put("totalCoinsExpiring", userExpiringCoins.values().stream().mapToInt(Integer::intValue).sum());
            stats.put("lastCacheUpdate", now.format(DATE_FORMATTER));
            
            redisTemplate.opsForValue().set(EXPIRY_STATS_KEY, stats, 15, TimeUnit.MINUTES);
            
            log.info("Completed expiry cache update. Updated cache for {} users with {} total coins expiring", 
                    userExpiringCoins.size(), stats.get("totalCoinsExpiring"));
                    
        } catch (Exception e) {
            log.error("Error updating expiry cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes expired rewards for a specific user immediately.
     * This method is called during user operations to handle expiry in real-time.
     * Includes duplicate prevention logic.
     * 
     * @param userId The user ID to process expired rewards for
     * @return Number of expired transactions processed
     */
    public int processExpiredRewardsForUser(String userId) {
        log.info("Processing expired rewards for user: {}", userId);
        
        try {
            // Find expired rewards for this specific user
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("Checking for expired rewards for user {} at time: {}", userId, currentTime);
            List<Transaction> expiredRewards = transactionRepository
                    .findExpiredRewardTransactionsByUser(userId, currentTime);
            
            if (expiredRewards.isEmpty()) {
                log.info("No expired rewards found for user: {} at time: {}", userId, LocalDateTime.now());
                return 0;
            }
            
            log.info("Found {} expired rewards for user: {} at time: {}", expiredRewards.size(), userId, LocalDateTime.now());
            
            // Filter out rewards that have already been processed
            log.info("Before filtering: {} expired rewards found", expiredRewards.size());
            List<Transaction> unprocessedExpiredRewards = expiredRewards.stream()
                    .filter(reward -> {
                        boolean alreadyProcessed = hasBeenProcessedForExpiry(userId, reward);
                        log.info("Reward {} (coins: {}, expires: {}) - Already processed: {}", 
                                reward.getTransactionId(), reward.getNumberOfCoins(), reward.getExpiresAt(), alreadyProcessed);
                        return !alreadyProcessed;
                    })
                    .collect(Collectors.toList());
            
            if (unprocessedExpiredRewards.isEmpty()) {
                log.info("All expired rewards for user {} have already been processed", userId);
                return 0;
            }
            
            log.info("Found {} unprocessed expired rewards for user: {}", unprocessedExpiredRewards.size(), userId);
            
            int processedCount = 0;
            User user = null;
            
            for (Transaction expiredReward : unprocessedExpiredRewards) {
                try {
                    // Get user if not already loaded
                    if (user == null) {
                        user = expiredReward.getUser();
                    }
                    
                    // Create expiry transaction immediately
                    Transaction expiryTransaction = Transaction.createExpiryTransaction(
                            user, expiredReward.getNumberOfCoins());
                    
                    // Save expiry transaction
                    transactionRepository.save(expiryTransaction);
                    
                    // Note: We don't update user.coins anymore since balance is calculated from transactions
                    
                    processedCount++;
                    
                    log.info("Processed expiry for user {}: {} coins from transaction {}", 
                            userId, expiredReward.getNumberOfCoins(), expiredReward.getTransactionId());
                            
                } catch (Exception e) {
                    log.error("Failed to process expiry for transaction {}: {}", 
                            expiredReward.getTransactionId(), e.getMessage(), e);
                }
            }
            
            // Save updated user balance if any expiries were processed
            if (processedCount > 0 && user != null) {
                userRepository.save(user);
                log.info("Processed {} expired transactions for user {}", processedCount, userId);
            }
            
            return processedCount;
            
        } catch (Exception e) {
            log.error("Error processing expired rewards for user {}: {}", userId, e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Checks if a reward transaction has already been processed for expiry.
     * This prevents duplicate expiry processing by using a more precise matching approach.
     * 
     * @param userId The user ID
     * @param rewardTransaction The reward transaction to check
     * @return true if already processed, false otherwise
     */
    private boolean hasBeenProcessedForExpiry(String userId, Transaction rewardTransaction) {
        try {
            // Look for expiry transactions that match this specific reward
            List<Transaction> expiryTransactions = transactionRepository
                    .findByUserIdAndTransactionType(userId, TransactionType.EXPIRY);
            
            // Check if there's an expiry transaction that corresponds to this reward
            return expiryTransactions.stream()
                    .anyMatch(expiry -> {
                        // Match by number of coins and timing
                        boolean coinsMatch = expiry.getNumberOfCoins().equals(rewardTransaction.getNumberOfCoins());
                        boolean createdAfterReward = expiry.getCreatedAt().isAfter(rewardTransaction.getCreatedAt());
                        boolean createdAfterExpiry = expiry.getCreatedAt().isAfter(rewardTransaction.getExpiresAt().minusSeconds(30));
                        
                        // Additional check: ensure the expiry was created within a reasonable time window
                        boolean withinReasonableTime = expiry.getCreatedAt().isBefore(rewardTransaction.getExpiresAt().plusHours(1));
                        
                        return coinsMatch && createdAfterReward && createdAfterExpiry && withinReasonableTime;
                    });
        } catch (Exception e) {
            log.warn("Error checking if reward has been processed for expiry: {}", e.getMessage());
            return false; // If we can't determine, allow processing to be safe
        }
    }
    
    /**
     * Scheduled task that runs every hour as a cleanup job.
     * This is now a backup mechanism to catch any missed expiries.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hour in milliseconds
    public void cleanupExpiredRewards() {
        log.info("Starting expired rewards cleanup task");
        
        try {
            int processedCount = processAllExpiredRewards();
            
            // Update processing statistics in Redis
            Map<String, Object> processingStats = new HashMap<>();
            processingStats.put("lastCleanupRun", LocalDateTime.now().format(DATE_FORMATTER));
            processingStats.put("expiredTransactionsProcessed", processedCount);
            
            redisTemplate.opsForValue().set("reward:expiry:cleanup:stats", 
                    processingStats, 24, TimeUnit.HOURS);
            
            if (processedCount > 0) {
                log.warn("Cleanup processed {} expired transactions that were missed by real-time processing", processedCount);
            } else {
                log.info("Cleanup completed - no expired transactions found (real-time processing working correctly)");
            }
            
        } catch (Exception e) {
            log.error("Error during expired rewards cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Processes all expired reward transactions (used by cleanup job).
     * This method replaces the call to RewardManagementService to avoid circular dependency.
     * 
     * @return Number of expired transactions processed
     */
    private int processAllExpiredRewards() {
        log.info("Starting expired rewards processing");
        
        try {
            List<Transaction> expiredRewards = transactionRepository
                    .findExpiredRewardTransactions(LocalDateTime.now());
            
            int processedCount = 0;
            
            for (Transaction expiredReward : expiredRewards) {
                try {
                    // Create expiry transaction
                    Transaction expiryTransaction = Transaction.createExpiryTransaction(
                            expiredReward.getUser(), expiredReward.getNumberOfCoins());
                    
                    // Save expiry transaction
                    transactionRepository.save(expiryTransaction);
                    
                    // Get fresh user instance to avoid lazy loading issues  
                    User user = userRepository.findByUserId(expiredReward.getUser().getUserId())
                            .orElseThrow(() -> new UserNotFoundException(expiredReward.getUser().getUserId()));
                    
                    // Note: We don't update user.coins anymore since balance is calculated from transactions
                    
                    processedCount++;
                    
                    log.info("Processed expiry for user {}: {} coins", 
                            user.getUserId(), expiredReward.getNumberOfCoins());
                            
                } catch (Exception e) {
                    log.error("Failed to process expiry for transaction {}: {}", 
                            expiredReward.getTransactionId(), e.getMessage(), e);
                }
            }
            
            log.info("Completed expired rewards processing. Processed {} transactions", processedCount);
            return processedCount;
            
        } catch (Exception e) {
            log.error("Failed to process expired rewards: {}", e.getMessage(), e);
            throw new RewardManagementException("Failed to process expired rewards: " + e.getMessage(), e);
        }
    }
    
    /**
     * Manual trigger for processing expired rewards for a specific user.
     * 
     * @param userId The user ID to process expired rewards for
     * @return Success message with processed count
     */
    public String triggerExpiredRewardsProcessingForUser(String userId) {
        log.info("Manual trigger for expired rewards processing for user: {}", userId);
        int processedCount = processExpiredRewardsForUser(userId);
        return String.format("Processed %d expired transactions for user %s", processedCount, userId);
    }
}
