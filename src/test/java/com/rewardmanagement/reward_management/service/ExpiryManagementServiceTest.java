package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.repository.TransactionRepository;
import com.rewardmanagement.reward_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpiryManagementService Tests")
class ExpiryManagementServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FifoCoinsService fifoCoinsService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private ExpiryManagementService expiryManagementService;

    private User testUser;
    private Transaction expiredReward;
    private Transaction activeReward;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("testUser");
        testUser.setCoins(100);

        LocalDateTime now = LocalDateTime.now();

        // Create expired reward by setting expiry in the future first, then manually setting it to past
        expiredReward = Transaction.createRewardTransaction(testUser, 100, now.plusMinutes(60));
        expiredReward.setTransactionId(UUID.randomUUID());
        expiredReward.setCreatedAt(now.minusMinutes(60));
        expiredReward.setExpiresAt(now.minusMinutes(10)); // Manually set to past for testing

        activeReward = Transaction.createRewardTransaction(testUser, 200, now.plusMinutes(30));
        activeReward.setTransactionId(UUID.randomUUID());
        activeReward.setCreatedAt(now.minusMinutes(30));
    }

    @Test
    @DisplayName("Should process expired rewards for user successfully")
    void shouldProcessExpiredRewardsForUserSuccessfully() {
        // Given
        String userId = "testUser";
        LocalDateTime now = LocalDateTime.now();
        
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);
        Transaction expiryTransaction = Transaction.createExpiryTransaction(testUser, 75, expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(75);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward, 75)).thenReturn(expiryTransaction);
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        assertEquals(1, result);
        verify(transactionRepository).findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class));
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward, 75);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should skip processing when no expired rewards found")
    void shouldSkipProcessingWhenNoExpiredRewardsFound() {
        // Given
        String userId = "testUser";

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        verify(transactionRepository).findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class));
        verify(fifoCoinsService, never()).expireCoinsFromReward(any(), any(), anyInt());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should skip rewards with zero remaining coins")
    void shouldSkipRewardsWithZeroRemainingCoins() {
        // Given
        String userId = "testUser";
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // The
        verify(fifoCoinsService, never()).expireCoinsFromReward(any(), any(), anyInt());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle null remaining coins by using original amount")
    void shouldHandleNullRemainingCoinsByUsingOriginalAmount() {
        // Given
        String userId = "testUser";
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);
        Transaction expiryTransaction = Transaction.createExpiryTransaction(testUser, 100, expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(null);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward, 100)).thenReturn(expiryTransaction);
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward, 100);
    }

    @Test
    @DisplayName("Should handle user not found gracefully")
    void shouldHandleUserNotFoundGracefully() {
        // Given
        String userId = "nonExistentUser";
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        assertEquals(0, result);
        verify(fifoCoinsService, never()).expireCoinsFromReward(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should handle exceptions during processing gracefully")
    void shouldHandleExceptionsDuringProcessingGracefully() {
        // Given
        String userId = "testUser";
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(75);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward, 75))
                .thenThrow(new RuntimeException("Processing failed"));

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        assertEquals(0, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process multiple expired rewards correctly")
    void shouldProcessMultipleExpiredRewardsCorrectly() {
        // Given
        String userId = "testUser";
        
        Transaction expiredReward2 = Transaction.createRewardTransaction(testUser, 50, LocalDateTime.now().plusMinutes(60));
        expiredReward2.setTransactionId(UUID.randomUUID());
        expiredReward2.setExpiresAt(LocalDateTime.now().minusMinutes(5)); // Manually set to past for testing
        
        List<Transaction> expiredRewards = Arrays.asList(expiredReward, expiredReward2);
        Transaction expiryTx1 = Transaction.createExpiryTransaction(testUser, 75, expiredReward);
        Transaction expiryTx2 = Transaction.createExpiryTransaction(testUser, 30, expiredReward2);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(75);
        when(transactionRepository.calculateRemainingCoins(expiredReward2.getTransactionId(), 50)).thenReturn(30);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward2, 30)).thenReturn(expiryTx2);
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward, 75);
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward2, 30);
        verify(userRepository).save(testUser);
    }


    @Test
    @DisplayName("Should handle partial processing correctly")
    void shouldHandlePartialProcessingCorrectly() {
        // Given
        String userId = "testUser";
        
        // Create two expired rewards, one already partially processed
        Transaction expiredReward2 = Transaction.createRewardTransaction(testUser, 60, LocalDateTime.now().plusMinutes(5));
        expiredReward2.setTransactionId(UUID.randomUUID());
        
        List<Transaction> expiredRewards = Arrays.asList(expiredReward, expiredReward2);
        Transaction expiryTx2 = Transaction.createExpiryTransaction(testUser, 40, expiredReward2);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);

        // First reward: 75 remaining coins
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(75);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward, 75))
                .thenReturn(Transaction.createExpiryTransaction(testUser, 75, expiredReward));
        
        // Second reward: 40 remaining coins
        when(transactionRepository.calculateRemainingCoins(expiredReward2.getTransactionId(), 60)).thenReturn(40);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward2, 40)).thenReturn(expiryTx2);
        
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward, 75);
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward2, 40);
    }

    @Test
    @DisplayName("Should handle edge case of very small remaining coins")
    void shouldHandleEdgeCaseOfVerySmallRemainingCoins() {
        // Given
        String userId = "testUser";
        List<Transaction> expiredRewards = Arrays.asList(expiredReward);
        Transaction expiryTransaction = Transaction.createExpiryTransaction(testUser, 1, expiredReward);

        when(transactionRepository.findExpiredRewardTransactionsByUser(eq(userId), any(LocalDateTime.class)))
                .thenReturn(expiredRewards);
        when(transactionRepository.calculateRemainingCoins(expiredReward.getTransactionId(), 100)).thenReturn(1);
        when(fifoCoinsService.expireCoinsFromReward(testUser, expiredReward, 1)).thenReturn(expiryTransaction);
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        int result = expiryManagementService.processExpiredRewardsForUser(userId);

        // Then
        verify(fifoCoinsService).expireCoinsFromReward(testUser, expiredReward, 1);
    }
}
