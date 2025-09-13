package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.exception.InsufficientBalanceException;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling FIFO (First In, First Out) coin operations.
 * Ensures that coins expiring first are redeemed/expired first.
 * Tracks which specific reward transactions coins are consumed from.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FifoCoinsService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Represents the remaining coins available from a specific reward transaction.
     */
    public static class AvailableReward {
        private final Transaction rewardTransaction;
        private final int remainingCoins;
        
        public AvailableReward(Transaction rewardTransaction, int remainingCoins) {
            this.rewardTransaction = rewardTransaction;
            this.remainingCoins = remainingCoins;
        }
        
        public Transaction getRewardTransaction() { return rewardTransaction; }
        public int getRemainingCoins() { return remainingCoins; }
    }
    
    /**
     * Gets available reward coins for a user in FIFO order (expiring first).
     * 
     * @param userId The user ID
     * @return List of available rewards ordered by expiry date
     */
    @Transactional(readOnly = true)
    public List<AvailableReward> getAvailableRewards(String userId) {
        LocalDateTime currentTime = LocalDateTime.now();
        List<Transaction> rewardTransactions = transactionRepository
                .findAvailableRewardTransactionsByUser(userId, currentTime);
        
        List<AvailableReward> availableRewards = new ArrayList<>();
        
        for (Transaction reward : rewardTransactions) {
            Integer remainingCoins = transactionRepository
                    .calculateRemainingCoins(reward.getTransactionId(), reward.getNumberOfCoins());
            
            if (remainingCoins == null) {
                remainingCoins = reward.getNumberOfCoins(); // No consumption transactions found
            }
            
            if (remainingCoins > 0) {
                availableRewards.add(new AvailableReward(reward, remainingCoins));
            }
        }
        
        log.debug("Found {} available rewards for user {} with total {} coins", 
                availableRewards.size(), userId, 
                availableRewards.stream().mapToInt(AvailableReward::getRemainingCoins).sum());
        
        return availableRewards;
    }
    
    /**
     * Redeems coins using FIFO logic - coins expiring first are redeemed first.
     * Creates multiple REDEEM transactions if coins come from multiple rewards.
     * 
     * @param user The user redeeming coins
     * @param coinsToRedeem Total number of coins to redeem
     * @return List of REDEEM transactions created
     * @throws InsufficientBalanceException if user doesn't have enough coins
     */
    @Transactional
    public List<Transaction> redeemCoinsWithFifo(User user, int coinsToRedeem) {
        log.info("Processing FIFO redemption for user {}: {} coins", user.getUserId(), coinsToRedeem);
        
        List<AvailableReward> availableRewards = getAvailableRewards(user.getUserId());
        
        // Check if user has enough coins
        int totalAvailable = availableRewards.stream()
                .mapToInt(AvailableReward::getRemainingCoins)
                .sum();
        
        if (totalAvailable < coinsToRedeem) {
            throw new InsufficientBalanceException(user.getUserId(), totalAvailable, coinsToRedeem);
        }
        
        List<Transaction> redeemTransactions = new ArrayList<>();
        int remainingToRedeem = coinsToRedeem;
        
        // Process rewards in FIFO order (expiring first)
        for (AvailableReward availableReward : availableRewards) {
            if (remainingToRedeem <= 0) break;
            
            int coinsFromThisReward = Math.min(remainingToRedeem, availableReward.getRemainingCoins());
            
            // Create REDEEM transaction linked to this specific reward
            Transaction redeemTransaction = Transaction.createRedeemTransaction(
                    user, coinsFromThisReward, availableReward.getRewardTransaction());
            
            Transaction savedTransaction = transactionRepository.save(redeemTransaction);
            redeemTransactions.add(savedTransaction);
            
            remainingToRedeem -= coinsFromThisReward;
            
            log.info("Redeemed {} coins from reward {} (expires: {}) for user {}", 
                    coinsFromThisReward, 
                    availableReward.getRewardTransaction().getTransactionId(),
                    availableReward.getRewardTransaction().getExpiresAt(),
                    user.getUserId());
        }
        
        log.info("Completed FIFO redemption for user {}: {} transactions created", 
                user.getUserId(), redeemTransactions.size());
        
        return redeemTransactions;
    }
    
    /**
     * Expires coins from a specific reward transaction using FIFO logic.
     * Creates an EXPIRY transaction linked to the reward transaction.
     * 
     * @param user The user whose coins are expiring
     * @param rewardTransaction The reward transaction to expire coins from
     * @param coinsToExpire Number of coins to expire (can be partial)
     * @return The EXPIRY transaction created
     */
    @Transactional
    public Transaction expireCoinsFromReward(User user, Transaction rewardTransaction, int coinsToExpire) {
        log.info("Expiring {} coins from reward {} for user {}", 
                coinsToExpire, rewardTransaction.getTransactionId(), user.getUserId());
        
        // Validate that the reward has enough remaining coins
        Integer remainingCoins = transactionRepository
                .calculateRemainingCoins(rewardTransaction.getTransactionId(), rewardTransaction.getNumberOfCoins());
        
        if (remainingCoins == null) {
            remainingCoins = rewardTransaction.getNumberOfCoins();
        }
        
        if (remainingCoins < coinsToExpire) {
            throw new IllegalArgumentException(
                    String.format("Cannot expire %d coins from reward %s, only %d remaining", 
                            coinsToExpire, rewardTransaction.getTransactionId(), remainingCoins));
        }
        
        // Create EXPIRY transaction linked to this specific reward
        Transaction expiryTransaction = Transaction.createExpiryTransaction(
                user, coinsToExpire, rewardTransaction);
        
        Transaction savedTransaction = transactionRepository.save(expiryTransaction);
        
        log.info("Created expiry transaction {} for {} coins from reward {} for user {}", 
                savedTransaction.getTransactionId(), coinsToExpire, 
                rewardTransaction.getTransactionId(), user.getUserId());
        
        return savedTransaction;
    }
    
    /**
     * Gets the total available balance for a user.
     * 
     * @param userId The user ID
     * @return Total available coins
     */
    @Transactional(readOnly = true)
    public int getTotalAvailableBalance(String userId) {
        return getAvailableRewards(userId).stream()
                .mapToInt(AvailableReward::getRemainingCoins)
                .sum();
    }
}
