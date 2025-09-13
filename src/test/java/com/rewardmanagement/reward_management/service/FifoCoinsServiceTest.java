package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.exception.InsufficientBalanceException;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import com.rewardmanagement.reward_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FifoCoinsService Tests")
class FifoCoinsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FifoCoinsService fifoCoinsService;

    private User testUser;
    private Transaction reward1;
    private Transaction reward2;
    private Transaction reward3;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("testUser");
        testUser.setCoins(100);

        LocalDateTime now = LocalDateTime.now();

        // Create test rewards in FIFO order (earliest expiry first)
        reward1 = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(10));
        reward1.setTransactionId(UUID.randomUUID());

        reward2 = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(30));
        reward2.setTransactionId(UUID.randomUUID());

        reward3 = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(60));
        reward3.setTransactionId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should get available rewards in FIFO order")
    void shouldGetAvailableRewardsInFifoOrder() {
        // Given
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2, reward3);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(100);
        when(transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200)).thenReturn(200);

        // When
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards("testUser");

        // Then
        assertEquals(3, availableRewards.size());
        assertEquals(reward1.getTransactionId(), availableRewards.get(0).getRewardTransaction().getTransactionId());
        assertEquals(50, availableRewards.get(0).getRemainingCoins());
        assertEquals(reward2.getTransactionId(), availableRewards.get(1).getRewardTransaction().getTransactionId());
        assertEquals(100, availableRewards.get(1).getRemainingCoins());
        assertEquals(reward3.getTransactionId(), availableRewards.get(2).getRewardTransaction().getTransactionId());
        assertEquals(200, availableRewards.get(2).getRemainingCoins());
    }

    @Test
    @DisplayName("Should filter out rewards with zero remaining coins")
    void shouldFilterOutRewardsWithZeroRemainingCoins() {
        // Given
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2, reward3);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(0); // Fully consumed
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(75);
        when(transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200)).thenReturn(200);

        // When
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards("testUser");

        // Then
        assertEquals(2, availableRewards.size());
        assertEquals(reward2.getTransactionId(), availableRewards.get(0).getRewardTransaction().getTransactionId());
        assertEquals(75, availableRewards.get(0).getRemainingCoins());
        assertEquals(reward3.getTransactionId(), availableRewards.get(1).getRewardTransaction().getTransactionId());
        assertEquals(200, availableRewards.get(1).getRemainingCoins());
    }

    @Test
    @DisplayName("Should calculate total available balance correctly")
    void shouldCalculateTotalAvailableBalanceCorrectly() {
        // Given
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2, reward3);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(30);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(75);
        when(transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200)).thenReturn(150);

        // When
        int totalBalance = fifoCoinsService.getTotalAvailableBalance("testUser");

        // Then
        assertEquals(255, totalBalance); // 30 + 75 + 150
    }

    @Test
    @DisplayName("Should return zero when no available rewards")
    void shouldReturnZeroWhenNoAvailableRewards() {
        // Given
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When
        int totalBalance = fifoCoinsService.getTotalAvailableBalance("testUser");

        // Then
        assertEquals(0, totalBalance);
    }

    @Test
    @DisplayName("Should redeem coins using FIFO logic successfully")
    void shouldRedeemCoinsUsingFifoLogicSuccessfully() {
        // Given
        int coinsToRedeem = 125; // Should consume reward1 (50) + part of reward2 (75)
        
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2, reward3);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(100);
        when(transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200)).thenReturn(200);

        Transaction mockRedeemTx1 = mock(Transaction.class);
        Transaction mockRedeemTx2 = mock(Transaction.class);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(mockRedeemTx1)
                .thenReturn(mockRedeemTx2);

        // When
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem);

        // Then
        assertEquals(2, redeemTransactions.size());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        
        // Verify the transactions were created with correct amounts and source rewards
        verify(transactionRepository).save(argThat(tx -> 
                tx.getTransactionType() == TransactionType.REDEEM &&
                tx.getNumberOfCoins() == 50 &&
                tx.getSourceReward().equals(reward1)));
        
        verify(transactionRepository).save(argThat(tx -> 
                tx.getTransactionType() == TransactionType.REDEEM &&
                tx.getNumberOfCoins() == 75 &&
                tx.getSourceReward().equals(reward2)));
    }

    @Test
    @DisplayName("Should throw exception when insufficient balance for redemption")
    void shouldThrowExceptionWhenInsufficientBalanceForRedemption() {
        // Given
        int coinsToRedeem = 500; // More than available
        
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(100);

        // When & Then
        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class, 
                () -> fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem));
        
        assertTrue(exception.getMessage().contains("testUser"));
        assertTrue(exception.getMessage().contains("150")); // Available balance
        assertTrue(exception.getMessage().contains("500")); // Requested amount
    }

    @Test
    @DisplayName("Should redeem exact available balance successfully")
    void shouldRedeemExactAvailableBalanceSuccessfully() {
        // Given
        int coinsToRedeem = 150; // Exact balance
        
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(100);

        Transaction mockRedeemTx1 = mock(Transaction.class);
        Transaction mockRedeemTx2 = mock(Transaction.class);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(mockRedeemTx1)
                .thenReturn(mockRedeemTx2);

        // When
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem);

        // Then
        assertEquals(2, redeemTransactions.size());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should expire coins from specific reward successfully")
    void shouldExpireCoinsFromSpecificRewardSuccessfully() {
        // Given
        int coinsToExpire = 30;
        Transaction mockExpiryTx = mock(Transaction.class);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockExpiryTx);
        when(transactionRepository.calculateRemainingCoins(any(), anyInt())).thenReturn(50);

        // When
        Transaction expiryTransaction = fifoCoinsService.expireCoinsFromReward(testUser, reward1, coinsToExpire);

        // Then
        assertNotNull(expiryTransaction);
        verify(transactionRepository).save(argThat(tx -> 
                tx.getTransactionType() == TransactionType.EXPIRY &&
                tx.getNumberOfCoins() == 30 &&
                tx.getSourceReward().equals(reward1) &&
                tx.getUser().equals(testUser)));
    }

    @Test
    @DisplayName("Should handle zero coins redemption gracefully")
    void shouldHandleZeroCoinsRedemptionGracefully() {
        // Given
        int coinsToRedeem = 0;

        // When
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem);

        // Then
        assertTrue(redeemTransactions.isEmpty());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should handle single reward partial redemption")
    void shouldHandleSingleRewardPartialRedemption() {
        // Given
        int coinsToRedeem = 25; // Less than first reward
        
        List<Transaction> mockRewards = Arrays.asList(reward1);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);

        Transaction mockRedeemTx = mock(Transaction.class);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockRedeemTx);

        // When
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem);

        // Then
        assertEquals(1, redeemTransactions.size());
        verify(transactionRepository).save(argThat(tx -> 
                tx.getTransactionType() == TransactionType.REDEEM &&
                tx.getNumberOfCoins() == 25 &&
                tx.getSourceReward().equals(reward1)));
    }

    @Test
    @DisplayName("Should handle multiple rewards full consumption")
    void shouldHandleMultipleRewardsFullConsumption() {
        // Given
        int coinsToRedeem = 150; // Exactly reward1 + reward2
        
        List<Transaction> mockRewards = Arrays.asList(reward1, reward2, reward3);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(50);
        when(transactionRepository.calculateRemainingCoins(reward2.getTransactionId(), 100)).thenReturn(100);
        when(transactionRepository.calculateRemainingCoins(reward3.getTransactionId(), 200)).thenReturn(200);

        Transaction mockRedeemTx1 = mock(Transaction.class);
        Transaction mockRedeemTx2 = mock(Transaction.class);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(mockRedeemTx1)
                .thenReturn(mockRedeemTx2);

        // When
        List<Transaction> redeemTransactions = fifoCoinsService.redeemCoinsWithFifo(testUser, coinsToRedeem);

        // Then
        assertEquals(2, redeemTransactions.size());
        verify(transactionRepository).save(argThat(tx -> 
                tx.getNumberOfCoins() == 50 && tx.getSourceReward().equals(reward1)));
        verify(transactionRepository).save(argThat(tx -> 
                tx.getNumberOfCoins() == 100 && tx.getSourceReward().equals(reward2)));
    }

    @Test
    @DisplayName("Should throw exception when expiring zero coins")
    void shouldThrowExceptionWhenExpiringZeroCoins() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> fifoCoinsService.expireCoinsFromReward(testUser, reward1, 0));
    }

    @Test
    @DisplayName("Should throw exception when expiring negative coins")
    void shouldThrowExceptionWhenExpiringNegativeCoins() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> fifoCoinsService.expireCoinsFromReward(testUser, reward1, -10));
    }

    @Test
    @DisplayName("Should handle repository returning null remaining coins")
    void shouldHandleRepositoryReturningNullRemainingCoins() {
        // Given
        List<Transaction> mockRewards = Arrays.asList(reward1);
        when(transactionRepository.findAvailableRewardTransactionsByUser(eq("testUser"), any(LocalDateTime.class)))
                .thenReturn(mockRewards);
        when(transactionRepository.calculateRemainingCoins(reward1.getTransactionId(), 50)).thenReturn(null);

        // When
        List<FifoCoinsService.AvailableReward> availableRewards = fifoCoinsService.getAvailableRewards("testUser");

        // Then
        assertEquals(1, availableRewards.size());
        assertEquals(50, availableRewards.get(0).getRemainingCoins()); // Should default to original coins
    }
}
