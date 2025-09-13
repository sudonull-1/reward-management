package com.rewardmanagement.reward_management.service;

import com.rewardmanagement.reward_management.dto.AvailableRewardResponse;
import com.rewardmanagement.reward_management.dto.TransactionResponse;
import com.rewardmanagement.reward_management.dto.ViewResult;
import com.rewardmanagement.reward_management.entity.Transaction;
import com.rewardmanagement.reward_management.entity.TransactionType;
import com.rewardmanagement.reward_management.entity.User;
import com.rewardmanagement.reward_management.exception.InsufficientBalanceException;
import com.rewardmanagement.reward_management.exception.UserNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RewardManagementService Tests")
class RewardManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FifoCoinsService fifoCoinsService;

    @Mock
    private ExpiryManagementService expiryManagementService;

    @InjectMocks
    private RewardManagementService rewardManagementService;

    private User testUser;
    private Transaction rewardTransaction;
    private Transaction redeemTransaction;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("testUser");
        testUser.setCoins(100);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        rewardTransaction = Transaction.createRewardTransaction(testUser, 100, LocalDateTime.now().plusMinutes(60));
        rewardTransaction.setTransactionId(UUID.randomUUID());
        rewardTransaction.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        redeemTransaction = Transaction.createRedeemTransaction(testUser, 30, rewardTransaction);
        redeemTransaction.setTransactionId(UUID.randomUUID());
        redeemTransaction.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        // Set up the expiry management service
        rewardManagementService.setExpiryManagementService(expiryManagementService);
    }

    @Test
    @DisplayName("Should credit reward successfully for existing user")
    void shouldCreditRewardSuccessfullyForExistingUser() {
        // Given
        String userId = "testUser";
        Integer numberOfCoins = 100;
        Integer expirationMinutes = 60;

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(rewardTransaction);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        rewardManagementService.creditReward(userId, numberOfCoins, expirationMinutes);

        // Then
        verify(userRepository).findByUserId(userId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(userRepository).save(testUser);
        assertEquals(200, testUser.getCoins()); // 100 + 100
    }

    @Test
    @DisplayName("Should create new user when crediting reward to non-existing user")
    void shouldCreateNewUserWhenCreditingRewardToNonExistingUser() {
        // Given
        String userId = "newUser";
        Integer numberOfCoins = 50;
        Integer expirationMinutes = 30;

        when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(rewardTransaction);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        rewardManagementService.creditReward(userId, numberOfCoins, expirationMinutes);

        // Then
        verify(userRepository).findByUserId(userId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(userRepository, times(2)).save(any(User.class)); // Once for creation, once for update
    }

    @Test
    @DisplayName("Should redeem reward successfully using FIFO")
    void shouldRedeemRewardSuccessfullyUsingFifo() {
        // Given
        String userId = "testUser";
        Integer numberOfCoins = 75;

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(fifoCoinsService.getTotalAvailableBalance(userId)).thenReturn(150);
        when(fifoCoinsService.redeemCoinsWithFifo(testUser, numberOfCoins))
                .thenReturn(Arrays.asList(redeemTransaction));

        // When
        rewardManagementService.redeemReward(userId, numberOfCoins);

        // Then
        verify(expiryManagementService).processExpiredRewardsForUser(userId);
        verify(fifoCoinsService).getTotalAvailableBalance(userId);
        verify(fifoCoinsService).redeemCoinsWithFifo(testUser, numberOfCoins);
    }

    @Test
    @DisplayName("Should throw exception when redeeming from non-existing user")
    void shouldThrowExceptionWhenRedeemingFromNonExistingUser() {
        // Given
        String userId = "nonExistentUser";
        Integer numberOfCoins = 50;

        when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, 
                () -> rewardManagementService.redeemReward(userId, numberOfCoins));
        
        verify(userRepository).findByUserId(userId);
        verify(fifoCoinsService, never()).redeemCoinsWithFifo(any(), anyInt());
    }

    @Test
    @DisplayName("Should throw exception when insufficient balance for redemption")
    void shouldThrowExceptionWhenInsufficientBalanceForRedemption() {
        // Given
        String userId = "testUser";
        Integer numberOfCoins = 200;

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(fifoCoinsService.getTotalAvailableBalance(userId)).thenReturn(50);

        // When & Then
        assertThrows(InsufficientBalanceException.class, 
                () -> rewardManagementService.redeemReward(userId, numberOfCoins));
        
        verify(fifoCoinsService).getTotalAvailableBalance(userId);
        verify(fifoCoinsService, never()).redeemCoinsWithFifo(any(), anyInt());
    }

    @Test
    @DisplayName("Should get user balance successfully")
    void shouldGetUserBalanceSuccessfully() {
        // Given
        String userId = "testUser";
        Integer expectedBalance = 150;

        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(userRepository.getUserBalance(userId)).thenReturn(Optional.of(expectedBalance));

        // When
        Integer balance = rewardManagementService.getUserBalance(userId);

        // Then
        assertEquals(expectedBalance, balance);
        verify(expiryManagementService).processExpiredRewardsForUser(userId);
        verify(userRepository).getUserBalance(userId);
    }

    @Test
    @DisplayName("Should throw exception when getting balance for non-existing user")
    void shouldThrowExceptionWhenGettingBalanceForNonExistingUser() {
        // Given
        String userId = "nonExistentUser";

        when(userRepository.getUserBalance(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, 
                () -> rewardManagementService.getUserBalance(userId));
    }

    @Test
    @DisplayName("Should view user information successfully")
    void shouldViewUserInformationSuccessfully() {
        // Given
        String userId = "testUser";
        LocalDateTime now = LocalDateTime.now();

        List<Transaction> transactions = Arrays.asList(rewardTransaction, redeemTransaction);
        FifoCoinsService.AvailableReward availableReward = new FifoCoinsService.AvailableReward(rewardTransaction, 70);
        List<FifoCoinsService.AvailableReward> availableRewards = Arrays.asList(availableReward);

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(transactions);
        when(fifoCoinsService.getAvailableRewards(userId)).thenReturn(availableRewards);
        when(transactionRepository.findConsumptionTransactionsByReward(rewardTransaction.getTransactionId()))
                .thenReturn(Arrays.asList(redeemTransaction));

        // When
        ViewResult result = rewardManagementService.view(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(70, result.getTotalCoins());
        assertEquals(2, result.getTransactions().size());
        assertEquals(1, result.getAvailableRewards().size());
        assertEquals(1, result.getActiveRewardTransactions());
        
        AvailableRewardResponse availableRewardResponse = result.getAvailableRewards().get(0);
        assertEquals(rewardTransaction.getTransactionId(), availableRewardResponse.getRewardTransactionId());
        assertEquals(100, availableRewardResponse.getOriginalCoins());
        assertEquals(70, availableRewardResponse.getRemainingCoins());
        assertEquals(30, availableRewardResponse.getRedeemedCoins());
        assertEquals(0, availableRewardResponse.getExpiredCoins());
        assertEquals(1, availableRewardResponse.getFifoOrder());

        verify(expiryManagementService).processExpiredRewardsForUser(userId);
        verify(fifoCoinsService).getAvailableRewards(userId);
    }

    @Test
    @DisplayName("Should throw exception when viewing non-existing user")
    void shouldThrowExceptionWhenViewingNonExistingUser() {
        // Given
        String userId = "nonExistentUser";

        when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, 
                () -> rewardManagementService.view(userId));
    }

    @Test
    @DisplayName("Should calculate coins expiring in 30 minutes correctly")
    void shouldCalculateCoinsExpiringIn30MinutesCorrectly() {
        // Given
        String userId = "testUser";
        LocalDateTime now = LocalDateTime.now();
        
        // Create reward expiring in 20 minutes (should be counted)
        Transaction expiringReward = Transaction.createRewardTransaction(testUser, 50, now.plusMinutes(20));
        expiringReward.setTransactionId(UUID.randomUUID());
        
        FifoCoinsService.AvailableReward availableReward1 = new FifoCoinsService.AvailableReward(expiringReward, 50);
        FifoCoinsService.AvailableReward availableReward2 = new FifoCoinsService.AvailableReward(rewardTransaction, 70); // Expires in 60 mins
        
        List<FifoCoinsService.AvailableReward> availableRewards = Arrays.asList(availableReward1, availableReward2);

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Arrays.asList(rewardTransaction));
        when(fifoCoinsService.getAvailableRewards(userId)).thenReturn(availableRewards);
        when(transactionRepository.findConsumptionTransactionsByReward(any())).thenReturn(Collections.emptyList());

        // When
        ViewResult result = rewardManagementService.view(userId);

        // Then
        assertEquals(50, result.getCoinsExpiringIn30Mins()); // Only the reward expiring in 20 minutes
    }

    @Test
    @DisplayName("Should handle empty transaction history")
    void shouldHandleEmptyTransactionHistory() {
        // Given
        String userId = "testUser";

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Collections.emptyList());
        when(fifoCoinsService.getAvailableRewards(userId)).thenReturn(Collections.emptyList());

        // When
        ViewResult result = rewardManagementService.view(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(0, result.getTotalCoins());
        assertTrue(result.getTransactions().isEmpty());
        assertTrue(result.getAvailableRewards().isEmpty());
        assertEquals(0, result.getActiveRewardTransactions());
        assertEquals(0, result.getCoinsExpiringIn30Mins());
    }

    @Test
    @DisplayName("Should convert transactions to response DTOs correctly")
    void shouldConvertTransactionsToResponseDtosCorrectly() {
        // Given
        String userId = "testUser";
        List<Transaction> transactions = Arrays.asList(rewardTransaction, redeemTransaction);

        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(testUser));
        when(expiryManagementService.processExpiredRewardsForUser(userId)).thenReturn(0);
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(transactions);
        when(fifoCoinsService.getAvailableRewards(userId)).thenReturn(Collections.emptyList());

        // When
        ViewResult result = rewardManagementService.view(userId);

        // Then
        assertEquals(2, result.getTransactions().size());
        
        TransactionResponse rewardResponse = result.getTransactions().stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.REWARD)
                .findFirst().orElse(null);
        assertNotNull(rewardResponse);
        assertEquals(100, rewardResponse.getBalanceImpact());
        
        TransactionResponse redeemResponse = result.getTransactions().stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.REDEEM)
                .findFirst().orElse(null);
        assertNotNull(redeemResponse);
        assertEquals(-30, redeemResponse.getBalanceImpact());
    }
}
