package com.rewardmanagement.reward_management.exception;

import com.rewardmanagement.reward_management.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the reward management system.
 * Provides centralized exception handling across all controllers.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles UserNotFoundException.
     * 
     * @param ex The UserNotFoundException
     * @param request The web request
     * @return ResponseEntity with error response
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles InsufficientBalanceException.
     * 
     * @param ex The InsufficientBalanceException
     * @param request The web request
     * @return ResponseEntity with error response
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientBalanceException(
            InsufficientBalanceException ex, WebRequest request) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("userId", ex.getUserId());
        details.put("currentBalance", ex.getCurrentBalance());
        details.put("requestedAmount", ex.getRequestedAmount());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles TransactionNotFoundException.
     * 
     * @param ex The TransactionNotFoundException
     * @param request The web request
     * @return ResponseEntity with error response
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleTransactionNotFoundException(
            TransactionNotFoundException ex, WebRequest request) {
        log.warn("Transaction not found: {}", ex.getMessage());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles InvalidTransactionException.
     * 
     * @param ex The InvalidTransactionException
     * @param request The web request
     * @return ResponseEntity with error response
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidTransactionException(
            InvalidTransactionException ex, WebRequest request) {
        log.warn("Invalid transaction: {}", ex.getMessage());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles general RewardManagementException.
     * 
     * @param ex The RewardManagementException
     * @param request The web request
     * @return ResponseEntity with error response
     */
    @ExceptionHandler(RewardManagementException.class)
    public ResponseEntity<ApiResponse<Object>> handleRewardManagementException(
            RewardManagementException ex, WebRequest request) {
        log.error("Reward management error: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> errorResponse = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles validation errors from request body validation.
     * 
     * @param ex The MethodArgumentNotValidException
     * @param request The web request
     * @return ResponseEntity with validation error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        String errorMessage = "Validation failed for request parameters";
        ApiResponse<Object> errorResponse = ApiResponse.error(errorMessage, "VALIDATION_ERROR");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles constraint violation exceptions.
     * 
     * @param ex The ConstraintViolationException
     * @param request The web request
     * @return ResponseEntity with constraint violation error response
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(
            "Constraint violation: " + ex.getMessage(), "CONSTRAINT_VIOLATION");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles missing request header exceptions.
     * 
     * @param ex The MissingRequestHeaderException
     * @param request The web request
     * @return ResponseEntity with missing header error response
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(
            MissingRequestHeaderException ex, WebRequest request) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        
        String errorMessage = "Missing required header: " + ex.getHeaderName();
        ApiResponse<Object> errorResponse = ApiResponse.error(errorMessage, "MISSING_HEADER");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles method argument type mismatch exceptions.
     * 
     * @param ex The MethodArgumentTypeMismatchException
     * @param request The web request
     * @return ResponseEntity with type mismatch error response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.warn("Method argument type mismatch: {}", ex.getMessage());
        
        String errorMessage = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s", 
            ex.getValue(), ex.getName(), ex.getRequiredType().getSimpleName());
        ApiResponse<Object> errorResponse = ApiResponse.error(errorMessage, "TYPE_MISMATCH");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles HTTP message not readable exceptions (malformed JSON).
     * 
     * @param ex The HttpMessageNotReadableException
     * @param request The web request
     * @return ResponseEntity with malformed request error response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());
        
        ApiResponse<Object> errorResponse = ApiResponse.error(
            "Malformed JSON request", "MALFORMED_REQUEST");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles data integrity violation exceptions.
     * 
     * @param ex The DataIntegrityViolationException
     * @param request The web request
     * @return ResponseEntity with data integrity error response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, WebRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> errorResponse = ApiResponse.error(
            "Data integrity violation. Please check your request data.", "DATA_INTEGRITY_ERROR");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles database access exceptions.
     * 
     * @param ex The DataAccessException
     * @param request The web request
     * @return ResponseEntity with database error response
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccessException(
            DataAccessException ex, WebRequest request) {
        log.error("Database access error: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> errorResponse = ApiResponse.error(
            "Database operation failed. Please try again later.", "DATABASE_ERROR");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles all other unexpected exceptions.
     * 
     * @param ex The Exception
     * @param request The web request
     * @return ResponseEntity with generic error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> errorResponse = ApiResponse.error(
            "An unexpected error occurred. Please try again later.", "INTERNAL_ERROR");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
