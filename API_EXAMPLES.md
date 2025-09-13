# API Examples for Reward Management System

This document provides example API calls for testing the reward management system.

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
All endpoints require a `userId` header for user identification.

## API Endpoints

### 1. Credit Reward
Award coins to a user with configurable expiration.

```bash
curl -X POST "http://localhost:8080/api/v1/rewards" \
  -H "Content-Type: application/json" \
  -H "userId: user123" \
  -d '{
    "numberOfCoins": 100,
    "expirationMinutes": 30
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Successfully credited 100 coins to user user123",
  "data": null,
  "errorCode": null,
  "timestamp": "2024-09-13T10:30:45.123"
}
```

### 2. Redeem Coins
Redeem coins from a user's account.

```bash
curl -X POST "http://localhost:8080/api/v1/redeem" \
  -H "Content-Type: application/json" \
  -H "userId: user123" \
  -d '{
    "numberOfCoins": 25
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Successfully redeemed 25 coins from user user123",
  "data": null,
  "errorCode": null,
  "timestamp": "2024-09-13T10:31:15.456"
}
```

### 3. View Balance & Transaction History
Get comprehensive user information including balance and transaction history.

```bash
curl -X GET "http://localhost:8080/api/v1/view/coins" \
  -H "userId: user123"
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved information for user user123",
  "data": {
    "userId": "user123",
    "totalCoins": 75,
    "transactions": [
      {
        "transactionId": "550e8400-e29b-41d4-a716-446655440000",
        "transactionType": "REDEEM",
        "numberOfCoins": 25,
        "expiresAt": null,
        "createdAt": "2024-09-13T10:31:15.456",
        "balanceImpact": -25,
        "isExpired": false
      },
      {
        "transactionId": "550e8400-e29b-41d4-a716-446655440001",
        "transactionType": "REWARD",
        "numberOfCoins": 100,
        "expiresAt": "2024-10-13T10:30:45.123",
        "createdAt": "2024-09-13T10:30:45.123",
        "balanceImpact": 100,
        "isExpired": false
      }
    ],
    "coinsExpiringIn30Days": 75,
    "activeRewardTransactions": 1,
    "generatedAt": "2024-09-13T10:32:00.789"
  },
  "errorCode": null,
  "timestamp": "2024-09-13T10:32:00.789"
}
```

### 4. Get Current Balance (Lightweight)
Quick balance check without transaction history.

```bash
curl -X GET "http://localhost:8080/api/v1/balance" \
  -H "userId: user123"
```

**Response:**
```json
{
  "success": true,
  "message": "Current balance for user user123",
  "data": 75,
  "errorCode": null,
  "timestamp": "2024-09-13T10:32:30.123"
}
```

### 5. Health Check
Verify service availability.

```bash
curl -X GET "http://localhost:8080/api/v1/health"
```

**Response:**
```json
{
  "success": true,
  "message": "Reward Management Service is healthy",
  "data": "OK",
  "errorCode": null,
  "timestamp": "2024-09-13T10:33:00.456"
}
```

## Error Responses

### User Not Found
```json
{
  "success": false,
  "message": "User not found with ID: nonexistent-user",
  "data": null,
  "errorCode": "USER_NOT_FOUND",
  "timestamp": "2024-09-13T10:33:30.789"
}
```

### Insufficient Balance
```json
{
  "success": false,
  "message": "Insufficient balance for user user123. Current balance: 10, Requested: 50",
  "data": null,
  "errorCode": "INSUFFICIENT_BALANCE",
  "timestamp": "2024-09-13T10:34:00.123"
}
```

### Validation Error
```json
{
  "success": false,
  "message": "Validation failed for request parameters",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2024-09-13T10:34:30.456"
}
```

## Testing Scenarios

### 1. Complete User Journey
```bash
# 1. Credit initial rewards
curl -X POST "http://localhost:8080/api/v1/rewards" \
  -H "Content-Type: application/json" \
  -H "userId: testuser" \
  -d '{"numberOfCoins": 500, "expirationMinutes": 90}'

# 2. Check balance
curl -X GET "http://localhost:8080/api/v1/balance" \
  -H "userId: testuser"

# 3. Redeem some coins
curl -X POST "http://localhost:8080/api/v1/redeem" \
  -H "Content-Type: application/json" \
  -H "userId: testuser" \
  -d '{"numberOfCoins": 150}'

# 4. View complete history
curl -X GET "http://localhost:8080/api/v1/view/coins" \
  -H "userId: testuser"
```

### 2. Error Testing
```bash
# Test insufficient balance
curl -X POST "http://localhost:8080/api/v1/redeem" \
  -H "Content-Type: application/json" \
  -H "userId: testuser" \
  -d '{"numberOfCoins": 10000}'

# Test invalid input
curl -X POST "http://localhost:8080/api/v1/rewards" \
  -H "Content-Type: application/json" \
  -H "userId: testuser" \
  -d '{"numberOfCoins": -100}'

# Test missing header
curl -X GET "http://localhost:8080/api/v1/balance"
```

## Notes

- All timestamps are in ISO 8601 format
- Transaction IDs are UUIDs
- Balance impacts are signed integers (positive for credit, negative for debit)
- Expiry dates are calculated from the current time plus the specified days
- The system automatically handles coin expiry through scheduled jobs
