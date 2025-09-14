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
    private final FifoCoinsService fifoCoinsService;
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
                                   FifoCoinsService fifoCoinsService,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.fifoCoinsService = fifoCoinsService;
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
            
            // Repository query already filters out processed rewards, so we can process all returned rewards
            log.info("Found {} unprocessed expired rewards for user: {}", expiredRewards.size(), userId);
            
            int processedCount = 0;
            User user = null;
            
            for (Transaction expiredReward : expiredRewards) {
                try {
                    // Get user if not already loaded
                    if (user == null) {
                        user = expiredReward.getUser();
                    }
                    
                    // Calculate remaining coins for this specific reward
                    Integer remainingCoins = transactionRepository
                            .calculateRemainingCoins(expiredReward.getTransactionId(), expiredReward.getNumberOfCoins());
                    
                    if (remainingCoins == null) {
                        remainingCoins = expiredReward.getNumberOfCoins();
                    }
                    
                    if (remainingCoins > 0) {
                        // Use FIFO service to expire the remaining coins from this specific reward
                        Transaction expiryTransaction = fifoCoinsService.expireCoinsFromReward(
                                user, expiredReward, remainingCoins);
                        
                        processedCount++;
                        
                        log.info("Processed expiry for user {}: {} remaining coins from reward {} (original: {})", 
                                userId, remainingCoins, expiredReward.getTransactionId(), expiredReward.getNumberOfCoins());
                    } else {
                        log.debug("Reward {} for user {} already fully consumed, skipping expiry", 
                                expiredReward.getTransactionId(), userId);
                    }
                            
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
     * This prevents duplicate expiry processing by checking for existing expiry transactions
     * that are linked to this reward via source_reward_id.
     * 
     * @param userId The user ID
     * @param rewardTransaction The reward transaction to check
     * @return true if already processed, false otherwise
     */
    private boolean hasBeenProcessedForExpiry(String userId, Transaction rewardTransaction) {
        try {
            // Look for expiry transactions that are directly linked to this reward via source_reward_id
            List<Transaction> linkedExpiryTransactions = transactionRepository
                    .findConsumptionTransactionsByReward(rewardTransaction.getTransactionId());
            
            // Check if there are any EXPIRY transactions linked to this reward
            boolean hasExpiryTransaction = linkedExpiryTransactions.stream()
                    .anyMatch(tx -> tx.getTransactionType() == TransactionType.EXPIRY);
            
            if (hasExpiryTransaction) {
                log.debug("Reward {} already has expiry transactions linked to it", rewardTransaction.getTransactionId());
                return true;
            }
            
            return false;
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
     * Now uses the same FIFO logic as real-time processing to ensure consistency.
     * 
     * @return Number of expired transactions processed
     */
    private int processAllExpiredRewards() {
        log.info("Starting cleanup expired rewards processing");
        
        try {
            List<Transaction> expiredRewards = transactionRepository
                    .findExpiredRewardTransactions(LocalDateTime.now());
            
            if (expiredRewards.isEmpty()) {
                log.info("No expired rewards found during cleanup");
                return 0;
            }
            
            log.info("Found {} expired rewards during cleanup", expiredRewards.size());
            
            int processedCount = 0;
            
            // Group by user to process efficiently
            Map<String, List<Transaction>> expiredByUser = expiredRewards.stream()
                    .collect(Collectors.groupingBy(t -> t.getUser().getUserId()));
            
            for (Map.Entry<String, List<Transaction>> entry : expiredByUser.entrySet()) {
                String userId = entry.getKey();
                List<Transaction> userExpiredRewards = entry.getValue();
                
                try {
                    User user = userRepository.findByUserId(userId)
                            .orElseThrow(() -> new UserNotFoundException(userId));
                    
                    for (Transaction expiredReward : userExpiredRewards) {
                        try {
                            // Calculate remaining coins for this specific reward
                            Integer remainingCoins = transactionRepository
                                    .calculateRemainingCoins(expiredReward.getTransactionId(), expiredReward.getNumberOfCoins());
                            
                            if (remainingCoins == null) {
                                remainingCoins = expiredReward.getNumberOfCoins();
                            }
                            
                            if (remainingCoins > 0) {
                                // Use FIFO service to expire the remaining coins from this specific reward
                                Transaction expiryTransaction = fifoCoinsService.expireCoinsFromReward(
                                        user, expiredReward, remainingCoins);
                                
                                processedCount++;
                                
                                log.info("Cleanup processed expiry for user {}: {} remaining coins from reward {} (original: {})", 
                                        userId, remainingCoins, expiredReward.getTransactionId(), expiredReward.getNumberOfCoins());
                            } else {
                                log.debug("Reward {} for user {} already fully consumed during cleanup, skipping expiry", 
                                        expiredReward.getTransactionId(), userId);
                            }
                        } catch (Exception e) {
                            log.error("Failed to process expiry during cleanup for transaction {}: {}", 
                                    expiredReward.getTransactionId(), e.getMessage(), e);
                        }
                    }
                    
                    // Save user after processing all their expired rewards
                    if (!userExpiredRewards.isEmpty()) {
                        userRepository.save(user);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to process expired rewards for user {} during cleanup: {}", userId, e.getMessage(), e);
                }
            }
            
            log.info("Completed cleanup expired rewards processing. Processed {} transactions", processedCount);
            return processedCount;
            
        } catch (Exception e) {
            log.error("Failed to process expired rewards during cleanup: {}", e.getMessage(), e);
            throw new RewardManagementException("Failed to process expired rewards during cleanup: " + e.getMessage(), e);
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
