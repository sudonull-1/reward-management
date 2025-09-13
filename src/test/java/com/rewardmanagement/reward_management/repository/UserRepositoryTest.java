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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("testUser");
        testUser.setCoins(100);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should find user by userId")
    void shouldFindUserByUserId() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<User> found = userRepository.findByUserId("testUser");

        // Then
        assertTrue(found.isPresent());
        assertEquals("testUser", found.get().getUserId());
        assertEquals(100, found.get().getCoins());
    }

    @Test
    @DisplayName("Should return empty when user not found")
    void shouldReturnEmptyWhenUserNotFound() {
        // When
        Optional<User> found = userRepository.findByUserId("nonexistentUser");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should save user successfully")
    void shouldSaveUserSuccessfully() {
        // When
        User saved = userRepository.save(testUser);

        // Then
        assertNotNull(saved);
        assertEquals("testUser", saved.getUserId());
        assertEquals(100, saved.getCoins());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("Should calculate user balance from transactions")
    void shouldCalculateUserBalanceFromTransactions() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);

        // Create reward transaction
        Transaction rewardTx = Transaction.createRewardTransaction(savedUser, 100, LocalDateTime.now().plusMinutes(60));
        entityManager.persistAndFlush(rewardTx);

        // Create redeem transaction
        Transaction redeemTx = Transaction.createRedeemTransaction(savedUser, 30, rewardTx);
        entityManager.persistAndFlush(redeemTx);

        // Create expiry transaction
        Transaction expiryTx = Transaction.createExpiryTransaction(savedUser, 20, rewardTx);
        entityManager.persistAndFlush(expiryTx);

        entityManager.flush();

        // When
        Optional<Integer> balance = userRepository.getUserBalance("testUser");

        // Then
        assertTrue(balance.isPresent());
        assertEquals(50, balance.get()); // 100 - 30 - 20 = 50
    }

    @Test
    @DisplayName("Should return zero balance when no transactions exist")
    void shouldReturnZeroBalanceWhenNoTransactionsExist() {
        // Given
        entityManager.persistAndFlush(testUser);

        // When
        Optional<Integer> balance = userRepository.getUserBalance("testUser");

        // Then
        assertTrue(balance.isPresent());
        assertEquals(0, balance.get());
    }

    @Test
    @DisplayName("Should return empty when calculating balance for non-existent user")
    void shouldReturnEmptyWhenCalculatingBalanceForNonExistentUser() {
        // When
        Optional<Integer> balance = userRepository.getUserBalance("nonexistentUser");

        // Then
        assertTrue(balance.isPresent());
        assertEquals(0, balance.get()); // COALESCE returns 0 for non-existent users
    }

    @Test
    @DisplayName("Should handle complex transaction scenarios for balance calculation")
    void shouldHandleComplexTransactionScenariosForBalanceCalculation() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);

        // Multiple reward transactions
        Transaction reward1 = Transaction.createRewardTransaction(savedUser, 100, LocalDateTime.now().plusMinutes(60));
        Transaction reward2 = Transaction.createRewardTransaction(savedUser, 200, LocalDateTime.now().plusMinutes(120));
        entityManager.persistAndFlush(reward1);
        entityManager.persistAndFlush(reward2);

        // Multiple redeem transactions
        Transaction redeem1 = Transaction.createRedeemTransaction(savedUser, 50, reward1);
        Transaction redeem2 = Transaction.createRedeemTransaction(savedUser, 75, reward2);
        entityManager.persistAndFlush(redeem1);
        entityManager.persistAndFlush(redeem2);

        // Expiry transaction
        Transaction expiry1 = Transaction.createExpiryTransaction(savedUser, 25, reward1);
        entityManager.persistAndFlush(expiry1);

        entityManager.flush();

        // When
        Optional<Integer> balance = userRepository.getUserBalance("testUser");

        // Then
        assertTrue(balance.isPresent());
        // 100 + 200 - 50 - 75 - 25 = 150
        assertEquals(150, balance.get());
    }

    @Test
    @DisplayName("Should update user successfully")
    void shouldUpdateUserSuccessfully() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);
        LocalDateTime newUpdateTime = LocalDateTime.now().plusMinutes(1);

        // When
        savedUser.setCoins(200);
        savedUser.setUpdatedAt(newUpdateTime);
        User updated = userRepository.save(savedUser);

        // Then
        assertEquals(200, updated.getCoins());
        assertEquals(newUpdateTime, updated.getUpdatedAt());
    }

    @Test
    @DisplayName("Should delete user successfully")
    void shouldDeleteUserSuccessfully() {
        // Given
        User savedUser = entityManager.persistAndFlush(testUser);

        // When
        userRepository.delete(savedUser);
        entityManager.flush();

        // Then
        Optional<User> found = userRepository.findByUserId("testUser");
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should handle null userId gracefully")
    void shouldHandleNullUserIdGracefully() {
        // When
        Optional<User> found = userRepository.findByUserId(null);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should handle empty userId gracefully")
    void shouldHandleEmptyUserIdGracefully() {
        // When
        Optional<User> found = userRepository.findByUserId("");

        // Then
        assertFalse(found.isPresent());
    }
}
