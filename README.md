# Reward Management System

A comprehensive Spring Boot application for managing user reward coins with advanced FIFO (First-In-First-Out) processing, automatic expiry management, and complete transaction traceability.

## Overview

The Reward Management System allows businesses to:
- **Credit reward coins** to users with expiration dates
- **Redeem coins** using FIFO logic (coins expiring first are used first)
- **Track partial transactions** - see exactly which rewards coins came from
- **Automatic expiry processing** - expired coins are handled in real-time
- **Complete audit trail** - every coin movement is tracked and traceable

## System Architecture

### High-Level Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST Client   │    │   Web Browser   │    │   Mobile App    │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │     Spring Boot App      │
                    │   (Reward Management)    │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │      Service Layer       │
                    │  • RewardManagementSvc   │
                    │  • FifoCoinsService      │
                    │  • ExpiryManagementSvc   │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │    Repository Layer      │
                    │  • UserRepository        │
                    │  • TransactionRepository │
                    └─────────────┬─────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
┌─────────▼─────────┐  ┌─────────▼─────────┐  ┌─────────▼─────────┐
│   PostgreSQL      │  │      Redis        │  │   Scheduled       │
│   (Primary DB)    │  │    (Caching)      │  │     Jobs          │
│                   │  │                   │  │                   │
│ • Users           │  │ • Expiry Cache    │  │ • Auto Expiry     │
│ • Transactions    │  │ • Session Data    │  │ • Cache Updates   │
└───────────────────┘  └───────────────────┘  └───────────────────┘
```

### Low-Level Design (LLD)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              REWARD MANAGEMENT SYSTEM                           │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│  REST Controller │
│                 │
│ RewardController│
│ • /rewards      │ ──┐
│ • /redeem       │   │
│ • /view/coins   │   │
│ • /admin/*      │   │
└─────────────────┘   │
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              SERVICE LAYER                                     │
├─────────────────┬─────────────────┬─────────────────┬─────────────────────────┤
│RewardMgmtService│ FifoCoinsService│ExpiryMgmtService│    Scheduled Jobs       │
│                 │                 │                 │                         │
│• creditReward() │• getAvailable   │• processExpired │• @Scheduled            │
│• redeemReward() │  Rewards()      │  RewardsForUser │  cleanupExpired()       │
│• view()         │• redeemCoins    │• expireCoins    │• @Scheduled            │
│• getUserBalance │  WithFifo()     │  FromReward()   │  updateExpiryCache()    │
│                 │• getTotalAvail  │• hasBeenProc    │                         │
│                 │  ableBalance()  │  essedForExpiry │                         │
└─────────────────┴─────────────────┴─────────────────┴─────────────────────────┘
                      │                       │
                      ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            REPOSITORY LAYER                                    │
├─────────────────────────────┬───────────────────────────────────────────────────┤
│      UserRepository         │           TransactionRepository                   │
│                             │                                                   │
│• findByUserId()             │• findByUserIdOrderByCreatedAtDesc()              │
│• getUserBalance()           │• findExpiredRewardTransactionsByUser()           │
│• save()                     │• findAvailableRewardTransactionsByUser()         │
│• findUsersWithMinCoins()    │• calculateRemainingCoins()                       │
│                             │• findConsumptionTransactionsByReward()           │
│                             │• findUserRewardsExpiringBefore()                 │
│                             │• save()                                           │
└─────────────────────────────┴───────────────────────────────────────────────────┘
                      │                       │
                      ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ENTITY LAYER                                      │
├─────────────────────────────┬───────────────────────────────────────────────────┤
│           User              │                Transaction                        │
│                             │                                                   │
│• userId (PK)                │• transactionId (PK)                              │
│• coins                      │• user (FK → User)                                │
│• createdAt                  │• sourceReward (FK → Transaction)            │
│• updatedAt                  │• transactionType (REWARD/REDEEM/EXPIRY)          │
│                             │• numberOfCoins                                    │
│• addCoins()                 │• expiresAt                                        │
│• removeCoins()              │• createdAt                                        │
│• hasSufficientBalance()     │• updatedAt                                        │
│                             │                                                   │
│                             │• isExpired()                                      │
│                             │• getBalanceImpact()                              │
│                             │• createRewardTransaction()                       │
│                             │• createRedeemTransaction() ★ FIFO               │
│                             │• createExpiryTransaction() ★ FIFO               │
└─────────────────────────────┴───────────────────────────────────────────────────┘
```

## Core Features

### 1. FIFO Coin Management
- **Smart Ordering**: Coins expiring soonest are always used first
- **Partial Transactions**: Single rewards can be partially redeemed/expired
- **Source Tracking**: Every REDEEM/EXPIRY transaction knows which reward it came from
- **Complete Transparency**: Users see exactly which rewards have remaining coins

### 2. Real-Time Expiry Processing
- **Automatic Detection**: Expired coins are processed during any user operation
- **Instant Updates**: No waiting for scheduled jobs
- **Duplicate Prevention**: Smart logic prevents double-processing
- **Audit Trail**: All expiries create EXPIRY transactions

### 3. Comprehensive Transaction History
- **REWARD**: Coins credited to user
- **REDEEM**: Coins spent by user (with source reward tracking)
- **EXPIRY**: Coins expired (with source reward tracking)
- **Balance Calculation**: Always computed from transaction history

### 4. Scheduled Background Jobs
- **Cleanup Job**: Runs every 10 minutes to catch any missed expiries
- **Cache Updates**: Updates Redis with expiry information every 10 minutes
- **Performance**: Minimal impact due to real-time processing handling most cases

## Business Logic Explained

### How FIFO Works (Simple Example)

Imagine you have 3 gift cards:
1. **Card A**: $50, expires tomorrow
2. **Card B**: $100, expires next week
3. **Card C**: $200, expires next month

When you want to spend $75:
- **FIFO System**: Uses Card A ($50) + part of Card B ($25)
- **Without FIFO**: Might use Card C and let Card A expire

**Result**: You save money by using cards that expire first!

### FIFO Logic Flow Example

```
User has 3 rewards:
┌─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
│   Reward    │   Coins     │  Expires    │ Remaining   │ FIFO Order  │
├─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ Reward A    │     50      │  2 mins     │     50      │      1      │ ← First
│ Reward B    │    100      │  5 mins     │    100      │      2      │
│ Reward C    │    200      │ 10 mins     │    200      │      3      │ ← Last
└─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘

User redeems 75 coins:
┌─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
│   Action    │   Source    │   Amount    │  Remaining  │   Status    │
├─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ REDEEM #1   │  Reward A   │     50      │      0      │ Fully Used  │
│ REDEEM #2   │  Reward B   │     25      │     75      │ Partial     │
│             │  Reward C   │      0      │    200      │ Untouched   │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘

Result: 2 REDEEM transactions created, each linked to its source reward
```

### Transaction Relationships

```
REWARD Transaction (ID: R1)
├── 100 coins granted
├── Expires in 5 minutes
└── Source for:
    ├── REDEEM Transaction (25 coins) → Links to R1
    ├── REDEEM Transaction (30 coins) → Links to R1  
    └── EXPIRY Transaction (45 coins) → Links to R1
    
Total: 25 + 30 + 45 = 100 coins accounted for 
```

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.6+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+

### Quick Setup

1. **Clone the repository**
```bash
git clone <repository-url>
cd reward-management
```

2. **Start databases**
```bash
docker-compose up -d
```

3. **Run the application**
```bash
./mvnw spring-boot:run
```

4. **Verify setup**
```bash
curl http://localhost:8080/actuator/health
```

##  API Reference

### 1. Credit Reward Coins

**Endpoint**: `POST /api/v1/rewards`

**Purpose**: Add reward coins to a user's account with expiration

```bash
curl -X POST "http://localhost:8080/api/v1/rewards" \
  -H "Content-Type: application/json" \
  -H "userId: john_doe" \
  -d '{
    "numberOfCoins": 100,
    "expirationMinutes": 60
  }'
```

**Response**:
```json
{
  "success": true,
  "message": "Successfully credited 100 coins to user john_doe",
  "data": null,
  "errorCode": null,
  "timestamp": "2025-09-13T18:04:09.542632"
}
```

### 2. Redeem Coins (FIFO)

**Endpoint**: `POST /api/v1/redeem`

**Purpose**: Redeem coins using FIFO logic (expiring first)

```bash
curl -X POST "http://localhost:8080/api/v1/redeem" \
  -H "Content-Type: application/json" \
  -H "userId: john_doe" \
  -d '{
    "numberOfCoins": 75
  }'
```

**What Happens**:
1. System finds all available rewards for user
2. Orders them by expiry date (FIFO)
3. Creates multiple REDEEM transactions if needed
4. Each REDEEM transaction links to its source reward

### 3. View User Balance & Rewards

**Endpoint**: `GET /api/v1/view/coins`

**Purpose**: See complete user information with FIFO-ordered rewards

```bash
curl -X GET "http://localhost:8080/api/v1/view/coins" \
  -H "userId: john_doe"
```

**Response**:
```json
{
  "success": true,
  "message": "Retrieved information for user john_doe",
  "data": {
    "userId": "john_doe",
    "totalCoins": 275,
    "transactions": [
      {
        "transactionId": "uuid-1",
        "transactionType": "REDEEM",
        "numberOfCoins": 25,
        "balanceImpact": -25,
        "createdAt": "2025-09-13T18:04:30.202803"
      }
    ],
    "coinsExpiringIn30Mins": 50,
    "activeRewardTransactions": 2,
    "availableRewards": [
      {
        "rewardTransactionId": "uuid-reward-1",
        "originalCoins": 100,
        "remainingCoins": 75,
        "redeemedCoins": 25,
        "expiredCoins": 0,
        "expiresAt": "2025-09-13T18:09:09.531549",
        "fifoOrder": 1
      }
    ]
  }
}
```

**Key Fields Explained**:
- **`totalCoins`**: Current available balance
- **`availableRewards`**: FIFO-ordered rewards with detailed breakdown
- **`fifoOrder`**: Priority order (1 = used first)
- **`remainingCoins`**: Available coins from this reward
- **`redeemedCoins`**: Already spent from this reward
- **`expiredCoins`**: Already expired from this reward

### 4. Manual Expiry Processing (Admin)

**Endpoint**: `POST /api/v1/admin/process-expired`

**Purpose**: Manually trigger expiry processing for testing

```bash
curl -X POST "http://localhost:8080/api/v1/admin/process-expired" \
  -H "userId: john_doe"
```

## Scheduled Jobs

### 1. Automatic Expiry Cleanup
- **Frequency**: Every 10 minutes
- **Purpose**: Backup cleanup for any missed expiries
- **Impact**: Minimal (real-time processing handles most cases)

```java
@Scheduled(fixedRate = 600000) // 10 minutes
public void cleanupExpiredRewards() {
    // Processes any rewards that weren't caught by real-time processing
    // This is a safety net - most expiries are handled in real-time
}
```

**Business Logic**:
1. Finds all expired REWARD transactions across all users
2. Calculates remaining coins for each expired reward
3. Creates EXPIRY transactions for remaining coins
4. Links each EXPIRY to its source REWARD transaction
5. Updates user balances accordingly

### 2. Expiry Cache Updates
- **Frequency**: Every 10 minutes
- **Purpose**: Updates Redis cache with upcoming expiries
- **Benefit**: Fast lookup for coins expiring soon

```java
@Scheduled(fixedRate = 600000) // 10 minutes  
public void updateExpiryCache() {
    // Updates cache with rewards expiring in next 30 minutes
    // Improves performance for "coinsExpiringIn30Mins" calculations
}
```

**Cache Structure**:
```
Redis Key: "reward:expiry:userId"
Value: {
  "coinsExpiring": 150,
  "rewardsExpiring": 3,
  "nextExpiryTime": "2025-09-13T18:30:00"
}
```

## Database Schema

### Users Table
```sql
CREATE TABLE users (
    user_id VARCHAR(255) PRIMARY KEY,
    coins INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Transactions Table **Enhanced with FIFO Support**
```sql
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    source_reward_id UUID,  -- Links REDEEM/EXPIRY to source REWARD
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('REWARD', 'REDEEM', 'EXPIRY')),
    number_of_coins INTEGER NOT NULL CHECK (number_of_coins > 0),
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (source_reward_id) REFERENCES transactions(transaction_id)  
);

-- Performance indexes
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_expires_at ON transactions(expires_at);
CREATE INDEX idx_transactions_source_reward ON transactions(source_reward_id);  
CREATE INDEX idx_transactions_type_created ON transactions(transaction_type, created_at);
```

**Key Schema Changes**:
- **`source_reward_id`**: Self-referencing foreign key to track transaction relationships
- **Enhanced Indexes**: Optimized for FIFO queries and source tracking
- **Referential Integrity**: Ensures data consistency across related transactions

## Configuration

### Application Properties
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/reward_management
spring.datasource.username=reward_user
spring.datasource.password=reward_password

# Redis Configuration  
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Scheduling Configuration
spring.task.scheduling.pool.size=5

# Jackson Configuration (for LocalDateTime)
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.deserialization.fail-on-unknown-properties=false
```

## Error Handling

### Common Error Responses

**Insufficient Balance**:
```json
{
  "success": false,
  "message": "Insufficient balance for user john_doe. Current balance: 50, Requested: 100",
  "errorCode": "INSUFFICIENT_BALANCE",
  "timestamp": "2025-09-13T18:04:30.205044"
}
```

**User Not Found**:
```json
{
  "success": false,
  "message": "User not found with ID: nonexistent_user",
  "errorCode": "USER_NOT_FOUND",
  "timestamp": "2025-09-13T18:04:30.205044"
}
```

## Troubleshooting

### Common Issues

**FIFO Not Working**:
1. Check if `source_reward_id` column exists in database
2. Verify transaction relationships are properly set
3. Check application logs for FIFO processing messages

**Expiry Not Processing**:
1. Verify scheduled jobs are enabled (`@EnableScheduling`)
2. Check for lazy loading exceptions in logs
3. Ensure real-time processing is working during user operations

### Debug Commands

```bash
# Check database schema
docker exec -it reward-postgres psql -U reward_user -d reward_management -c "\d transactions"

# View recent transactions with source tracking
docker exec -it reward-postgres psql -U reward_user -d reward_management -c "SELECT transaction_id, transaction_type, number_of_coins, source_reward_id, created_at FROM transactions ORDER BY created_at DESC LIMIT 10"

# Check Redis cache
docker exec -it reward-redis redis-cli KEYS "*expiry*"
```

---

**Built using Spring Boot, PostgreSQL, Redis, and modern Java practices**

**Key Features**: FIFO Processing • Real-time Expiry • Transaction Traceability • Scheduled Jobs • Complete Audit Trail