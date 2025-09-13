package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.dto.TransactionResponse;
import com.rewardmanagement.reward_management.dto.ViewResult;
import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.exception.InsufficientBalanceException;
import com.rewardmanagement.reward_management.exception.InvalidTransactionException;
import com.rewardmanagement.reward_management.exception.RewardManagementException;
import com.rewardmanagement.reward_management.exception.UserNotFoundException;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import com.rewardmanagement.reward_management.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class implementing the reward management business logic.
 * Handles reward credits, redemptions, and balance inquiries.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Service
@Slf4j
@Transactional
public class RewardManagementService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Constructor for dependency injection.
     * 
     * @param userRepository Repository for user operations
     * @param transactionRepository Repository for transaction operations
     */
    @Autowired
    public RewardManagementService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Credits reward coins to a user's account.
     * Creates a new user if the user doesn't exist.
     * 
     * @param userId The user ID to credit coins to
     * @param numberOfCoins Number of coins to credit (must be positive)
     * @param expirationMinutes Number of minutes until the reward expires (default: 60)
     * @throws InvalidTransactionException if numberOfCoins is not positive
     * @throws RewardManagementException if the operation fails
     */
    public void creditReward(String userId, int numberOfCoins, int expirationMinutes) {
        
        log.info("Processing reward credit - User: {}, Coins: {}, ExpirationMinutes: {}", 
                userId, numberOfCoins, expirationMinutes);
        
        // Validate input parameters
        if (numberOfCoins <= 0) {
            throw new InvalidTransactionException("Number of coins must be positive: " + numberOfCoins);
        }
        
        if (expirationMinutes <= 0) {
            throw new InvalidTransactionException("Expiration minutes must be positive: " + expirationMinutes);
        }
        
        try {
            // Get or create user
            User user = getOrCreateUser(userId);
            
            // Calculate expiration date
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
            
            // Create reward transaction
            Transaction rewardTransaction = Transaction.createRewardTransaction(user, numberOfCoins, expiresAt);
            
            // Save transaction first (for audit trail)
            transactionRepository.save(rewardTransaction);
            
            // Update user balance
            user.addCoins(numberOfCoins);
            userRepository.save(user);
            
            log.info("Successfully credited {} coins to user {}. New balance: {}", 
                    numberOfCoins, userId, user.getCoins());
                    
        } catch (Exception e) {
            log.error("Failed to credit reward for user {}: {}", userId, e.getMessage(), e);
            throw new RewardManagementException("Failed to credit reward: " + e.getMessage(), e);
        }
    }

    /**
     * Redeems coins from a user's account using FIFO strategy for expiring rewards.
     * Evicts cache after successful operation.
     * 
     * @param userId The user ID to redeem coins from
     * @param numberOfCoins Number of coins to redeem (must be positive)
     * @throws UserNotFoundException if user doesn't exist
     * @throws InsufficientBalanceException if user doesn't have enough coins
     * @throws InvalidTransactionException if numberOfCoins is not positive
     * @throws RewardManagementException if the operation fails
     */
    public void redeemReward(String userId, int numberOfCoins) {
        
        log.info("Processing reward redemption - User: {}, Coins: {}", userId, numberOfCoins);
        
        // Validate input parameters
        if (numberOfCoins <= 0) {
            throw new InvalidTransactionException("Number of coins must be positive: " + numberOfCoins);
        }
        
        try {
            // Get user
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            
            // Check sufficient balance
            if (!user.hasSufficientBalance(numberOfCoins)) {
                throw new InsufficientBalanceException(userId, user.getCoins(), numberOfCoins);
            }
            
            // Create redeem transaction
            Transaction redeemTransaction = Transaction.createRedeemTransaction(user, numberOfCoins);
            
            // Save transaction first (for audit trail)
            transactionRepository.save(redeemTransaction);
            
            // Update user balance
            user.removeCoins(numberOfCoins);
            userRepository.save(user);
            
            log.info("Successfully redeemed {} coins from user {}. New balance: {}", 
                    numberOfCoins, userId, user.getCoins());
                    
        } catch (UserNotFoundException | InsufficientBalanceException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to redeem coins for user {}: {}", userId, e.getMessage(), e);
            throw new RewardManagementException("Failed to redeem coins: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a user's current balance and transaction history.
     * 
     * @param userId The user ID to retrieve information for
     * @return ViewResult containing balance and transaction history
     * @throws UserNotFoundException if user doesn't exist
     * @throws RewardManagementException if the operation fails
     */
    @Transactional(readOnly = true)
    public ViewResult view(String userId) {
        
        log.debug("Retrieving view for user: {}", userId);
        
        try {
            // Get user
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            
            // Get all transactions for the user
            List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
            
            // Convert to response DTOs
            List<TransactionResponse> transactionResponses = transactions.stream()
                    .map(this::convertToTransactionResponse)
                    .collect(Collectors.toList());
            
            // Calculate coins expiring in next 30 minutes
            LocalDateTime thirtyMinutesFromNow = LocalDateTime.now().plusMinutes(30);
            List<Transaction> expiringTransactions = transactionRepository
                    .findUserRewardsExpiringBefore(userId, thirtyMinutesFromNow);
            
            int coinsExpiringIn30Mins = expiringTransactions.stream()
                    .mapToInt(Transaction::getBalanceImpact)
                    .sum();
            
            // Count active reward transactions
            List<Transaction> activeRewards = transactionRepository
                    .findActiveRewardTransactions(userId, LocalDateTime.now());
            
            // Build and return ViewResult
            ViewResult result = new ViewResult();
            result.setUserId(userId);
            result.setTotalCoins(user.getCoins());
            result.setTransactions(transactionResponses);
            result.setCoinsExpiringIn30Mins(coinsExpiringIn30Mins);
            result.setActiveRewardTransactions(activeRewards.size());
            result.setGeneratedAt(LocalDateTime.now());
            
            log.debug("Successfully retrieved view for user {}. Balance: {}, Transactions: {}", 
                    userId, user.getCoins(), transactions.size());
            
            return result;
            
        } catch (UserNotFoundException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve view for user {}: {}", userId, e.getMessage(), e);
            throw new RewardManagementException("Failed to retrieve user information: " + e.getMessage(), e);
        }
    }

    /**
     * Gets user balance from database.
     * 
     * @param userId The user ID
     * @return Current balance
     * @throws UserNotFoundException if user doesn't exist
     * @throws RewardManagementException if operation fails
     */
    @Transactional(readOnly = true)
    public Integer getUserBalance(String userId) {
        log.debug("Getting balance for user: {}", userId);
        
        try {
            return userRepository.getUserBalance(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get balance for user {}: {}", userId, e.getMessage(), e);
            throw new RewardManagementException("Failed to retrieve user balance: " + e.getMessage(), e);
        }
    }

    /**
     * Processes expired reward transactions.
     * This method is called by the scheduled job.
     * 
     * @return Number of expired transactions processed
     * @throws RewardManagementException if operation fails
     */
    public int processExpiredRewards() {
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
                    
                    // Update user balance
                    User user = expiredReward.getUser();
                    user.removeCoins(expiredReward.getNumberOfCoins());
                    userRepository.save(user);
                    
                    processedCount++;
                    
                    log.debug("Processed expiry for user {}: {} coins", 
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
     * Gets or creates a user with the given ID.
     * 
     * @param userId The user ID
     * @return User entity
     */
    private User getOrCreateUser(String userId) {
        return userRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creating new user: {}", userId);
            User newUser = new User();
            newUser.setUserId(userId);
            newUser.setCoins(0);
                    return userRepository.save(newUser);
                });
    }


    /**
     * Converts Transaction entity to TransactionResponse DTO.
     * 
     * @param transaction The transaction entity
     * @return TransactionResponse DTO
     */
    private TransactionResponse convertToTransactionResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setTransactionType(transaction.getTransactionType());
        response.setNumberOfCoins(transaction.getNumberOfCoins());
        response.setExpiresAt(transaction.getExpiresAt());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setBalanceImpact(transaction.getBalanceImpact());
        response.setIsExpired(transaction.isExpired());
        return response;
    }
}
