package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.exception.RewardManagementException;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
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

/**
 * Service responsible for managing coin expiry operations.
 * Runs scheduled tasks to process expired rewards and maintain expiry cache.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Service
@Slf4j
public class ExpiryManagementService {

    private final TransactionRepository transactionRepository;
    private final RewardManagementService rewardManagementService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String EXPIRY_CACHE_PREFIX = "reward:expiry:";
    private static final String EXPIRY_STATS_KEY = "reward:expiry:stats";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor for dependency injection.
     * 
     * @param transactionRepository Repository for transaction operations
     * @param rewardManagementService Service for reward management operations
     * @param redisTemplate Redis template for cache operations
     */
    @Autowired
    public ExpiryManagementService(TransactionRepository transactionRepository, 
                                 RewardManagementService rewardManagementService,
                                 RedisTemplate<String, Object> redisTemplate) {
        this.transactionRepository = transactionRepository;
        this.rewardManagementService = rewardManagementService;
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
     * Scheduled task that runs every hour to process and clean up expired reward transactions.
     * This task actually processes the expired rewards and deducts coins from user balances.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hour in milliseconds
    public void processExpiredRewards() {
        log.info("Starting expired rewards processing task");
        
        try {
            int processedCount = rewardManagementService.processExpiredRewards();
            
            // Update processing statistics in Redis
            Map<String, Object> processingStats = new HashMap<>();
            processingStats.put("lastProcessingRun", LocalDateTime.now().format(DATE_FORMATTER));
            processingStats.put("expiredTransactionsProcessed", processedCount);
            
            redisTemplate.opsForValue().set("reward:expiry:processing:stats", 
                    processingStats, 24, TimeUnit.HOURS);
            
            log.info("Completed expired rewards processing. Processed {} expired transactions", processedCount);
            
        } catch (Exception e) {
            log.error("Error processing expired rewards: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets expiry information for a specific user from Redis cache.
     * 
     * @param userId The user ID to get expiry information for
     * @return Map containing expiry information, or null if not found
     */
    public Map<String, Object> getUserExpiryInfo(String userId) {
        try {
            String cacheKey = EXPIRY_CACHE_PREFIX + userId;
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> expiryInfo = (Map<String, Object>) cachedData;
                return expiryInfo;
            }
            
            return null;
            
        } catch (Exception e) {
            log.warn("Error retrieving expiry info for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets general expiry statistics from Redis cache.
     * 
     * @return Map containing expiry statistics, or empty map if not found
     */
    public Map<String, Object> getExpiryStatistics() {
        try {
            Object cachedStats = redisTemplate.opsForValue().get(EXPIRY_STATS_KEY);
            
            if (cachedStats instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) cachedStats;
                return stats;
            }
            
            return new HashMap<>();
            
        } catch (Exception e) {
            log.warn("Error retrieving expiry statistics: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Manual trigger for expiry cache update (for testing or admin purposes).
     * 
     * @return Success message
     */
    public String triggerExpiryUpdate() {
        log.info("Manual trigger for expiry cache update");
        updateExpiryCache();
        return "Expiry cache update triggered successfully";
    }

    /**
     * Manual trigger for expired rewards processing (for testing or admin purposes).
     * 
     * @return Success message with processed count
     * @throws RewardManagementException if processing fails
     */
    public String triggerExpiredRewardsProcessing() {
        log.info("Manual trigger for expired rewards processing");
        int processedCount = rewardManagementService.processExpiredRewards();
        return String.format("Processed %d expired transactions", processedCount);
    }
}
