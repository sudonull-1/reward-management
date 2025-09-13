package com.rewardmanagement.reward_management.controller;

import com.rewardmanagement.reward_management.dto.ApiResponse;
import com.rewardmanagement.reward_management.dto.RedeemRequest;
import com.rewardmanagement.reward_management.dto.RewardRequest;
import com.rewardmanagement.reward_management.dto.ViewResult;
import com.rewardmanagement.reward_management.service.RewardManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for reward management operations.
 * Provides endpoints for crediting rewards, redeeming coins, and viewing balances.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@RestController
@RequestMapping("/api/v1")
@Validated
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class RewardController {

    private final RewardManagementService rewardManagementService;

    /**
     * Constructor for dependency injection.
     * 
     * @param rewardManagementService Service for reward management operations
     */
    @Autowired
    public RewardController(RewardManagementService rewardManagementService) {
        this.rewardManagementService = rewardManagementService;
    }

    /**
     * Credits reward coins to a user's account.
     * 
     * <p>API Specification:</p>
     * <ul>
     *   <li>Method: POST</li>
     *   <li>Path: /rewards</li>
     *   <li>Header: userId (required)</li>
     *   <li>Body: RewardRequest (numberOfCoins, expirationMinutes)</li>
     * </ul>
     * 
     * @param userId User ID from request header
     * @param request Request containing number of coins and expiration minutes
     * @return API response indicating success or failure
     */
    @PostMapping("/rewards")
    public ResponseEntity<ApiResponse<Object>> creditReward(
            @RequestHeader("userId") @NotBlank(message = "User ID is required") String userId,
            @Valid @RequestBody RewardRequest request) {
        
        log.info("Processing reward credit request - User: {}, Coins: {}, ExpirationMinutes: {}", 
                userId, request.getNumberOfCoins(), request.getExpirationMinutes());
        
        try {
            rewardManagementService.creditReward(userId, request.getNumberOfCoins(), request.getExpirationMinutes());
            
            String successMessage = String.format("Successfully credited %d coins to user %s", 
                    request.getNumberOfCoins(), userId);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(null, successMessage));
                    
        } catch (Exception e) {
            log.warn("Failed to credit reward for user {}: {}", userId, e.getMessage());
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * Redeems coins from a user's account.
     * 
     * <p>API Specification:</p>
     * <ul>
     *   <li>Method: POST</li>
     *   <li>Path: /redeem</li>
     *   <li>Header: userId (required)</li>
     *   <li>Body: RedeemRequest (numberOfCoins)</li>
     * </ul>
     * 
     * @param userId User ID from request header
     * @param request Request containing number of coins to redeem
     * @return API response indicating success or failure
     */
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<Object>> redeemReward(
            @RequestHeader("userId") @NotBlank(message = "User ID is required") String userId,
            @Valid @RequestBody RedeemRequest request) {
        
        log.info("Processing redemption request - User: {}, Coins: {}", 
                userId, request.getNumberOfCoins());
        
        try {
            rewardManagementService.redeemReward(userId, request.getNumberOfCoins());
            
            String successMessage = String.format("Successfully redeemed %d coins from user %s", 
                    request.getNumberOfCoins(), userId);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(null, successMessage));
                    
        } catch (Exception e) {
            log.warn("Failed to redeem coins for user {}: {}", userId, e.getMessage());
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * Retrieves a user's coin balance and transaction history.
     * 
     * <p>API Specification:</p>
     * <ul>
     *   <li>Method: GET</li>
     *   <li>Path: /view/coins</li>
     *   <li>Header: userId (required)</li>
     *   <li>Response: ViewResult (totalCoins, transactions, statistics)</li>
     * </ul>
     * 
     * @param userId User ID from request header
     * @return API response containing user's balance and transaction history
     */
    @GetMapping("/view/coins")
    public ResponseEntity<ApiResponse<ViewResult>> viewCoins(
            @RequestHeader("userId") @NotBlank(message = "User ID is required") String userId) {
        
        log.info("Processing view request for user: {}", userId);
        
        try {
            ViewResult result = rewardManagementService.view(userId);
            
            String successMessage = String.format("Retrieved information for user %s", userId);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(result, successMessage));
                    
        } catch (Exception e) {
            log.warn("Failed to retrieve information for user {}: {}", userId, e.getMessage());
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * Gets just the current balance for a user (lightweight endpoint).
     * 
     * <p>API Specification:</p>
     * <ul>
     *   <li>Method: GET</li>
     *   <li>Path: /balance</li>
     *   <li>Header: userId (required)</li>
     *   <li>Response: Current coin balance</li>
     * </ul>
     * 
     * @param userId User ID from request header
     * @return API response containing user's current balance
     */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Integer>> getUserBalance(
            @RequestHeader("userId") @NotBlank(message = "User ID is required") String userId) {
        
        log.info("Processing balance request for user: {}", userId);
        
        try {
            Integer balance = rewardManagementService.getUserBalance(userId);
            
            String successMessage = String.format("Current balance for user %s", userId);
            
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(balance, successMessage));
                    
        } catch (Exception e) {
            log.warn("Failed to retrieve balance for user {}: {}", userId, e.getMessage());
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * Health check endpoint to verify service availability.
     * 
     * @return Simple health check response
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("OK", "Reward Management Service is healthy"));
    }
}
