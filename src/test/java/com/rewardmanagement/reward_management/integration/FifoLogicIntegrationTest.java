package com.rewardmanagement.reward_management.integration;

import com.rewardmanagement.reward_management.dto.AvailableRewardResponse;
import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import com.rewardmanagement.reward_management.repository.UserRepository;
import com.rewardmanagement.reward_management.service.FifoCoinsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(FifoCoinsService.class)
@ActiveProfiles("test")
@DisplayName("FIFO Logic Integration Tests")
class FifoLogicIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FifoCoinsService fifoCoinsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("fifoTestUser");
        testUser.setCoins(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should process FIFO redemption correctly with database persistence")
    void shouldProcessFifoRedemptionCorrectlyWithDatabasePersistence() {
        // Given - Create rewards with different expiry times
        LocalDateTime now = LocalDateTime.now();
        
        Transaction reward1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(5));
        Transaction reward2 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(10));
        Transaction reward3 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(15));
        
        reward1 = transactionRepository.save(reward1);
        reward2 = transactionRepository.save(reward2);
        reward3 = transactionRepository.save(reward3);

        // When - Get available rewards in FIFO order
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards(testUser.getUserId());

        // Then - Verify FIFO ordering
        assertEquals(3, availableRewards.size());
        assertEquals(reward1.getTransactionId(), availableRewards.get(0).getRewardTransaction().getTransactionId());
        assertEquals(reward2.getTransactionId(), availableRewards.get(1).getRewardTransaction().getTransactionId());
        assertEquals(reward3.getTransactionId(), availableRewards.get(2).getRewardTransaction().getTransactionId());

        // Verify expiry order
        assertTrue(availableRewards.get(0).getRewardTransaction().getExpiresAt()
                .isBefore(availableRewards.get(1).getRewardTransaction().getExpiresAt()));
        assertTrue(availableRewards.get(1).getRewardTransaction().getExpiresAt()
                .isBefore(availableRewards.get(2).getRewardTransaction().getExpiresAt()));
    }

    @Test
    @DisplayName("Should redeem coins using FIFO with database transactions")
    void shouldRedeemCoinsUsingFifoWithDatabaseTransactions() {
        // Given - Create rewards
        LocalDateTime now = LocalDateTime.now();
        
        Transaction reward1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(5));
        Transaction reward2 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(10));
        Transaction reward3 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(15));
        
        reward1 = transactionRepository.save(reward1);
        reward2 = transactionRepository.save(reward2);
        reward3 = transactionRepository.save(reward3);

        // When - Redeem 125 coins (should consume reward1 fully + 75 from reward2)
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, 125);

        // Then - Verify correct redemption
        assertEquals(2, redeemTransactions.size());

        Transaction firstRedeem = redeemTransactions.get(0);
        assertEquals(TransactionType.REDEEM, firstRedeem.getTransactionType());
        assertEquals(50, firstRedeem.getNumberOfCoins());
        assertEquals(reward1.getTransactionId(), firstRedeem.getSourceReward().getTransactionId());

        Transaction secondRedeem = redeemTransactions.get(1);
        assertEquals(TransactionType.REDEEM, secondRedeem.getTransactionType());
        assertEquals(75, secondRedeem.getNumberOfCoins());
        assertEquals(reward2.getTransactionId(), secondRedeem.getSourceReward().getTransactionId());

        // Verify remaining coins calculation
        Integer remainingReward1 = transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50);
        Integer remainingReward2 = transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100);
        Integer remainingReward3 = transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200);

        assertEquals(0, remainingReward1); // Fully consumed
        assertEquals(25, remainingReward2); // 100 - 75 = 25
        assertEquals(200, remainingReward3); // Untouched
    }

    @Test
    @DisplayName("Should handle partial consumption and track source rewards")
    void shouldHandlePartialConsumptionAndTrackSourceRewards() {
        // Given - Create a single reward
        LocalDateTime now = LocalDateTime.now();
        Transaction reward = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(10));
        reward = transactionRepository.save(reward);

        // When - Partially redeem and expire
        List<Transaction> redeemTxs = fifoCoinsService.redeemCoinsWithFifo(testUser, 30);
        Transaction expiryTx = fifoCoinsService.expireCoinsFromReward(testUser, reward, 20);

        // Then - Verify transactions are linked correctly
        assertEquals(1, redeemTxs.size());
        Transaction redeemTx = redeemTxs.get(0);
        
        assertEquals(TransactionType.REDEEM, redeemTx.getTransactionType());
        assertEquals(30, redeemTx.getNumberOfCoins());
        assertEquals(reward.getTransactionId(), redeemTx.getSourceReward().getTransactionId());

        assertEquals(TransactionType.EXPIRY, expiryTx.getTransactionType());
        assertEquals(20, expiryTx.getNumberOfCoins());
        assertEquals(reward.getTransactionId(), expiryTx.getSourceReward().getTransactionId());

        // Verify remaining coins
        Integer remainingCoins = transactionRepository.calculateRemainingCoins(reward.getTransactionId(), 100);
        assertEquals(50, remainingCoins); // 100 - 30 - 20 = 50

        // Verify consumption transactions can be found
        List<Transaction> consumptionTxs = transactionRepository.findConsumptionTransactionsByReward(reward.getTransactionId());
        assertEquals(2, consumptionTxs.size());
        assertTrue(consumptionTxs.stream().anyMatch(tx -> tx.getTransactionType() == TransactionType.REDEEM));
        assertTrue(consumptionTxs.stream().anyMatch(tx -> tx.getTransactionType() == TransactionType.EXPIRY));
    }

    @Test
    @DisplayName("Should calculate total available balance correctly")
    void shouldCalculateTotalAvailableBalanceCorrectly() {
        // Given - Create rewards with different remaining amounts
        LocalDateTime now = LocalDateTime.now();
        
        Transaction reward1 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(5));
        Transaction reward2 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(10));
        
        reward1 = transactionRepository.save(reward1);
        reward2 = transactionRepository.save(reward2);

        // Partially consume reward1
        fifoCoinsService.redeemCoinsWithFifo(testUser, 30);

        // When - Calculate total available balance
        int totalBalance = fifoCoinsService.getTotalAvailableBalance(testUser.getUserId());

        // Then - Should be 270 (70 remaining from reward1 + 200 from reward2)
        assertEquals(270, totalBalance);
    }

    @Test
    @DisplayName("Should handle rewards with different expiry dates in FIFO ordering")
    void shouldHandleRewardsWithDifferentExpiryDatesInFifoOrdering() {
        // Given - Create rewards with different expiry dates
        LocalDateTime now = LocalDateTime.now();
        
        Transaction rewardWithExpiry = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(5));
        Transaction rewardWithLaterExpiry = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(20)); // Changed from null to future date
        Transaction anotherRewardWithExpiry = Transaction.createRewardTransaction(testUser, 150, now.plusMinutes(10));
        
        transactionRepository.save(rewardWithExpiry);
        transactionRepository.save(rewardWithLaterExpiry);
        transactionRepository.save(anotherRewardWithExpiry);

        // When - Get available rewards
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards(testUser.getUserId());

        // Then - All rewards should have expiry dates and be ordered correctly
        assertEquals(3, availableRewards.size());
        
        // All should have expiry dates and be ordered by expiry time
        assertNotNull(availableRewards.get(0).getRewardTransaction().getExpiresAt());
        assertNotNull(availableRewards.get(1).getRewardTransaction().getExpiresAt());
        assertNotNull(availableRewards.get(2).getRewardTransaction().getExpiresAt());
        
        // Should be ordered by expiry date (earliest first)
        assertTrue(availableRewards.get(0).getRewardTransaction().getExpiresAt()
                .isBefore(availableRewards.get(1).getRewardTransaction().getExpiresAt()));
        assertTrue(availableRewards.get(1).getRewardTransaction().getExpiresAt()
                .isBefore(availableRewards.get(2).getRewardTransaction().getExpiresAt()));
    }

    @Test
    @DisplayName("Should handle expired rewards correctly in FIFO queries")
    void shouldHandleExpiredRewardsCorrectlyInFifoQueries() {
        // Given - Create rewards with past and future expiry dates
        LocalDateTime now = LocalDateTime.now();
        
        Transaction expiredReward = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(60));
        expiredReward.setExpiresAt(now.minusMinutes(5)); // Manually set to past for testing
        Transaction activeReward1 = Transaction.createRewardTransaction(testUser, 150, now.plusMinutes(5));
        Transaction activeReward2 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(10));
        
        transactionRepository.save(expiredReward);
        transactionRepository.save(activeReward1);
        transactionRepository.save(activeReward2);

        // When - Get available rewards (should exclude expired ones)
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards(testUser.getUserId());

        // Then - Should only include active rewards
        assertEquals(2, availableRewards.size());
        assertTrue(availableRewards.stream().allMatch(reward -> 
                reward.getRewardTransaction().getExpiresAt() == null || 
                reward.getRewardTransaction().getExpiresAt().isAfter(now)));
    }

    @Test
    @DisplayName("Should handle edge case of zero remaining coins")
    void shouldHandleEdgeCaseOfZeroRemainingCoins() {
        // Given - Create reward and fully consume it
        LocalDateTime now = LocalDateTime.now();
        Transaction reward = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(10));
        reward = transactionRepository.save(reward);

        // Fully consume the reward
        fifoCoinsService.redeemCoinsWithFifo(testUser, 100);

        // When - Get available rewards
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards(testUser.getUserId());

        // Then - Should not include fully consumed rewards
        assertTrue(availableRewards.isEmpty());

        // And total balance should be zero
        int totalBalance = fifoCoinsService.getTotalAvailableBalance(testUser.getUserId());
        assertEquals(0, totalBalance);
    }

    @Test
    @DisplayName("Should maintain transaction integrity across multiple operations")
    void shouldMaintainTransactionIntegrityAcrossMultipleOperations() {
        // Given - Create multiple rewards
        LocalDateTime now = LocalDateTime.now();
        
        Transaction reward1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(5));
        Transaction reward2 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(10));
        Transaction reward3 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(15));
        
        reward1 = transactionRepository.save(reward1);
        reward2 = transactionRepository.save(reward2);
        reward3 = transactionRepository.save(reward3);

        // When - Perform multiple operations
        fifoCoinsService.redeemCoinsWithFifo(testUser, 75); // Consumes reward1 (50) + part of reward2 (25)
        fifoCoinsService.expireCoinsFromReward(testUser, reward2, 25); // Expire remaining part of reward2
        fifoCoinsService.redeemCoinsWithFifo(testUser, 100); // Consume part of reward3

        // Then - Verify final state
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards(testUser.getUserId());
        assertEquals(1, availableRewards.size());

        assertEquals(150, availableRewards.getFirst().getRemainingCoins()); // 200 - 100 = 100

        // Verify total balance
        int totalBalance = fifoCoinsService.getTotalAvailableBalance(testUser.getUserId());
        assertEquals(150, totalBalance);

        // Verify all transactions are properly linked
        List<Transaction> allTransactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(testUser.getUserId());
        long redeemCount = allTransactions.stream().filter(tx -> tx.getTransactionType() == TransactionType.REDEEM).count();
        long expiryCount = allTransactions.stream().filter(tx -> tx.getTransactionType() == TransactionType.EXPIRY).count();
        long rewardCount = allTransactions.stream().filter(tx -> tx.getTransactionType() == TransactionType.REWARD).count();

        assertEquals(3, rewardCount); // Original rewards
        assertEquals(4, redeemCount);
        assertEquals(1, expiryCount); // One expiry operation
    }
}
