package com.rewardmanagement.reward_management.repository;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TransactionRepository Tests")
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    private User testUser;
    private Transaction rewardTransaction;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("testUser");
        testUser.setCoins(100);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser = entityManager.persistAndFlush(testUser);

        rewardTransaction = Transaction.createRewardTransaction(testUser, 100, LocalDateTime.now().plusMinutes(60));
        rewardTransaction = entityManager.persistAndFlush(rewardTransaction);
    }

    @Test
    @DisplayName("Should find transactions by user ID ordered by created date desc")
    void shouldFindTransactionsByUserIdOrderedByCreatedDateDesc() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        Transaction tx1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(30));
        tx1.setCreatedAt(now.minusMinutes(10));
        
        Transaction tx2 = Transaction.createRedeemTransaction(testUser, 25, rewardTransaction);
        tx2.setCreatedAt(now.minusMinutes(5));
        
        Transaction tx3 = Transaction.createExpiryTransaction(testUser, 10, rewardTransaction);
        tx3.setCreatedAt(now);

        entityManager.persistAndFlush(tx1);
        entityManager.persistAndFlush(tx2);
        entityManager.persistAndFlush(tx3);

        // When
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc("testUser");

        // Then
        assertEquals(4, transactions.size()); // Including the setup rewardTransaction
        // Should be ordered by created date desc (newest first)
        assertTrue(transactions.get(0).getCreatedAt().isAfter(transactions.get(1).getCreatedAt()) ||
                  transactions.get(0).getCreatedAt().equals(transactions.get(1).getCreatedAt()));
    }

    @Test
    @DisplayName("Should find expired reward transactions by user")
    void shouldFindExpiredRewardTransactionsByUser() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // Expired reward - create with future date first, then manually set to past
        Transaction expiredReward = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(60));
        expiredReward.setExpiresAt(now.minusMinutes(10)); // Manually set to past for testing
        entityManager.persistAndFlush(expiredReward);
        
        // Active reward
        Transaction activeReward = Transaction.createRewardTransaction(testUser, 75, now.plusMinutes(30));
        entityManager.persistAndFlush(activeReward);
        
        // Non-reward transaction (should not be included)
        Transaction redeemTx = Transaction.createRedeemTransaction(testUser, 25, rewardTransaction);
        entityManager.persistAndFlush(redeemTx);

        // When
        List<Transaction> expiredRewards = transactionRepository.findExpiredRewardTransactionsByUser("testUser", now);

        // Then
        assertEquals(1, expiredRewards.size());
        assertEquals(expiredReward.getTransactionId(), expiredRewards.get(0).getTransactionId());
        assertEquals(TransactionType.REWARD, expiredRewards.get(0).getTransactionType());
        assertTrue(expiredRewards.get(0).getExpiresAt().isBefore(now));
    }

    @Test
    @DisplayName("Should find available reward transactions by user in FIFO order")
    void shouldFindAvailableRewardTransactionsByUserInFifoOrder() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // Create rewards with different expiry times
        Transaction reward1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(10)); // Expires first
        Transaction reward2 = Transaction.createRewardTransaction(testUser, 75, now.plusMinutes(30)); // Expires second
        Transaction reward3 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(60)); // Expires later
        Transaction expiredReward = Transaction.createRewardTransaction(testUser, 25, now.plusMinutes(60)); // Create with future date first
        expiredReward.setExpiresAt(now.minusMinutes(5)); // Manually set to past for testing
        
        entityManager.persistAndFlush(reward1);
        entityManager.persistAndFlush(reward2);
        entityManager.persistAndFlush(reward3);
        entityManager.persistAndFlush(expiredReward);

        // When
        List<Transaction> availableRewards = transactionRepository.findAvailableRewardTransactionsByUser("testUser", now);

        // Then
        assertEquals(4, availableRewards.size()); // Including setup rewardTransaction, excluding expired
        
        // Should be ordered by expiry date (FIFO) - nulls last
        Transaction first = availableRewards.get(0);
        Transaction second = availableRewards.get(1);
        
        assertNotNull(first.getExpiresAt());
        assertNotNull(second.getExpiresAt());
        assertTrue(first.getExpiresAt().isBefore(second.getExpiresAt()));
    }

    @Test
    @DisplayName("Should calculate remaining coins for a reward transaction")
    void shouldCalculateRemainingCoinsForRewardTransaction() {
        // Given
        Integer originalCoins = rewardTransaction.getNumberOfCoins();
        
        // Create consumption transactions
        Transaction redeem1 = Transaction.createRedeemTransaction(testUser, 30, rewardTransaction);
        Transaction redeem2 = Transaction.createRedeemTransaction(testUser, 20, rewardTransaction);
        Transaction expiry1 = Transaction.createExpiryTransaction(testUser, 15, rewardTransaction);
        
        entityManager.persistAndFlush(redeem1);
        entityManager.persistAndFlush(redeem2);
        entityManager.persistAndFlush(expiry1);

        // When
        Integer remainingCoins = transactionRepository.calculateRemainingCoins(
                rewardTransaction.getTransactionId(), originalCoins);

        // Then
        assertEquals(35, remainingCoins); // 100 - 30 - 20 - 15 = 35
    }

    @Test
    @DisplayName("Should return original coins when no consumption transactions exist")
    void shouldReturnOriginalCoinsWhenNoConsumptionTransactionsExist() {
        // Given
        Integer originalCoins = rewardTransaction.getNumberOfCoins();

        // When
        Integer remainingCoins = transactionRepository.calculateRemainingCoins(
                rewardTransaction.getTransactionId(), originalCoins);

        // Then
        assertEquals(originalCoins, remainingCoins);
    }

    @Test
    @DisplayName("Should find consumption transactions by reward")
    void shouldFindConsumptionTransactionsByReward() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        Transaction redeem1 = Transaction.createRedeemTransaction(testUser, 30, rewardTransaction);
        redeem1.setCreatedAt(now.minusMinutes(10));
        
        Transaction expiry1 = Transaction.createExpiryTransaction(testUser, 15, rewardTransaction);
        expiry1.setCreatedAt(now.minusMinutes(5));
        
        Transaction redeem2 = Transaction.createRedeemTransaction(testUser, 20, rewardTransaction);
        redeem2.setCreatedAt(now);
        
        entityManager.persistAndFlush(redeem1);
        entityManager.persistAndFlush(expiry1);
        entityManager.persistAndFlush(redeem2);

        // When
        List<Transaction> consumptionTxs = transactionRepository.findConsumptionTransactionsByReward(
                rewardTransaction.getTransactionId());

        // Then
        assertEquals(3, consumptionTxs.size());
        
        // Should be ordered by created date
        assertTrue(consumptionTxs.get(0).getCreatedAt().isBefore(consumptionTxs.get(1).getCreatedAt()));
        assertTrue(consumptionTxs.get(1).getCreatedAt().isBefore(consumptionTxs.get(2).getCreatedAt()));
        
        // Should contain both REDEEM and EXPIRY transactions
        assertTrue(consumptionTxs.stream().anyMatch(tx -> tx.getTransactionType() == TransactionType.REDEEM));
        assertTrue(consumptionTxs.stream().anyMatch(tx -> tx.getTransactionType() == TransactionType.EXPIRY));
    }

    @Test
    @DisplayName("Should find user rewards expiring before given time")
    void shouldFindUserRewardsExpiringBeforeGivenTime() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkTime = now.plusMinutes(30);
        
        // Create rewards with different expiry times
        Transaction expiringSoon = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(20)); // Expires before checkTime
        Transaction expiringLater = Transaction.createRewardTransaction(testUser, 75, now.plusMinutes(40)); // Expires after checkTime
        Transaction alreadyExpired = Transaction.createRewardTransaction(testUser, 25, now.plusMinutes(60)); // Create with future date first
        alreadyExpired.setExpiresAt(now.minusMinutes(10)); // Manually set to past for testing
        
        entityManager.persistAndFlush(expiringSoon);
        entityManager.persistAndFlush(expiringLater);
        entityManager.persistAndFlush(alreadyExpired);

        // When
        List<Transaction> expiringRewards = transactionRepository.findUserRewardsExpiringBefore("testUser", checkTime, now);

        // Then
        assertEquals(1, expiringRewards.size()); // expiringSoon + setup rewardTransaction
        assertTrue(expiringRewards.stream().allMatch(tx -> 
                tx.getExpiresAt() != null && 
                tx.getExpiresAt().isBefore(checkTime) && 
                tx.getExpiresAt().isAfter(now)));
    }

    @Test
    @DisplayName("Should return empty list when no transactions exist for user")
    void shouldReturnEmptyListWhenNoTransactionsExistForUser() {
        // When
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc("nonexistentUser");

        // Then
        assertTrue(transactions.isEmpty());
    }

    @Test
    @DisplayName("Should handle different expiry dates in FIFO ordering")
    void shouldHandleDifferentExpiryDatesInFifoOrdering() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        Transaction rewardWithExpiry = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(30));
        Transaction rewardWithLaterExpiry = Transaction.createRewardTransaction(testUser, 75, now.plusMinutes(60));
        
        entityManager.persistAndFlush(rewardWithExpiry);
        entityManager.persistAndFlush(rewardWithLaterExpiry);

        // When
        List<Transaction> availableRewards = transactionRepository.findAvailableRewardTransactionsByUser("testUser", now);

        // Then
        assertEquals(3, availableRewards.size()); // Including setup rewardTransaction
        
        // All rewards should have expiry dates and be ordered by expiry time
        assertTrue(availableRewards.stream().allMatch(tx -> tx.getExpiresAt() != null));
        
        // Should be ordered by expiry date (earliest first)
        for (int i = 0; i < availableRewards.size() - 1; i++) {
            assertTrue(availableRewards.get(i).getExpiresAt().isBefore(availableRewards.get(i + 1).getExpiresAt()) ||
                      availableRewards.get(i).getExpiresAt().equals(availableRewards.get(i + 1).getExpiresAt()));
        }
    }

    @Test
    @DisplayName("Should save transaction successfully")
    void shouldSaveTransactionSuccessfully() {
        // Given
        Transaction newTransaction = Transaction.createRedeemTransaction(testUser, 25, rewardTransaction);

        // When
        Transaction saved = transactionRepository.save(newTransaction);

        // Then
        assertNotNull(saved);
        assertNotNull(saved.getTransactionId());
        assertEquals(TransactionType.REDEEM, saved.getTransactionType());
        assertEquals(25, saved.getNumberOfCoins());
        assertEquals(rewardTransaction, saved.getSourceReward());
    }

    @Test
    @DisplayName("Should handle edge case of zero remaining coins")
    void shouldHandleEdgeCaseOfZeroRemainingCoins() {
        // Given
        Integer originalCoins = rewardTransaction.getNumberOfCoins();
        
        // Consume all coins
        Transaction redeem = Transaction.createRedeemTransaction(testUser, originalCoins, rewardTransaction);
        entityManager.persistAndFlush(redeem);

        // When
        Integer remainingCoins = transactionRepository.calculateRemainingCoins(
                rewardTransaction.getTransactionId(), originalCoins);

        // Then
        assertEquals(0, remainingCoins);
    }

    @Test
    @DisplayName("Should handle multiple users correctly")
    void shouldHandleMultipleUsersCorrectly() {
        // Given
        User anotherUser = new User();
        anotherUser.setUserId("anotherUser");
        anotherUser.setCoins(50);
        anotherUser.setCreatedAt(LocalDateTime.now());
        anotherUser.setUpdatedAt(LocalDateTime.now());
        anotherUser = entityManager.persistAndFlush(anotherUser);
        
        Transaction anotherUserReward = Transaction.createRewardTransaction(anotherUser, 200, LocalDateTime.now().plusMinutes(45));
        entityManager.persistAndFlush(anotherUserReward);

        // When
        List<Transaction> testUserTxs = transactionRepository.findByUserIdOrderByCreatedAtDesc("testUser");
        List<Transaction> anotherUserTxs = transactionRepository.findByUserIdOrderByCreatedAtDesc("anotherUser");

        // Then
        assertEquals(1, testUserTxs.size());
        assertEquals(1, anotherUserTxs.size());
        assertEquals("testUser", testUserTxs.get(0).getUser().getUserId());
        assertEquals("anotherUser", anotherUserTxs.get(0).getUser().getUserId());
    }
}
