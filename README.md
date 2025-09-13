# Reward Management System

A comprehensive Spring Boot application for managing user reward coins with expiry functionality, built with PostgreSQL and Redis.

## Features

- **Credit Rewards**: Award coins to users with configurable expiration dates
- **Redeem Coins**: Allow users to spend their earned coins
- **Balance Inquiry**: View current balance and transaction history
- **Automatic Expiry**: Scheduled jobs to handle coin expiration
- **Caching**: Redis-based caching for optimal performance
- **Exception Handling**: Comprehensive error handling with consistent API responses

## Architecture

### High-Level Design
- **API Gateway**: Load balancing, rate limiting, authentication, and authorization
- **Microservice**: Spring Boot application with business logic
- **Database**: PostgreSQL for persistent storage
- **Cache**: Redis for performance optimization
- **Scheduler**: Automated expiry management

### Database Schema
- **Users**: Store user information and current balance
- **Transactions**: Audit trail of all coin movements
- **Transaction Types**: REWARD, REDEEM, EXPIRY

## API Endpoints

### 1. Credit Rewards
```http
POST /api/v1/rewards
Header: userId: {user-id}
Content-Type: application/json

{
  "numberOfCoins": 100,
  "expirationMinutes": 60
}
```

### 2. Redeem Coins
```http
POST /api/v1/redeem
Header: userId: {user-id}
Content-Type: application/json

{
  "numberOfCoins": 50
}
```

### 3. View Balance & History
```http
GET /api/v1/view/coins
Header: userId: {user-id}
```

### 4. Get Current Balance
```http
GET /api/v1/balance
Header: userId: {user-id}
```

### 5. Health Check
```http
GET /api/v1/health
```

## Technology Stack

- **Framework**: Spring Boot 3.5.5
- **Language**: Java 21
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Build Tool**: Maven
- **Containerization**: Docker & Docker Compose

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- Docker and Docker Compose

### Running the Application

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd reward-management
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d postgres redis
   ```

3. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

   Or using Docker:
   ```bash
   docker-compose up
   ```

### Database Setup

The application automatically initializes the database schema using the SQL script in `src/main/resources/sql/01-init.sql`.

### Configuration

Key configuration properties in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/reward_management
spring.datasource.username=reward_user
spring.datasource.password=reward_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Cache TTL
spring.cache.redis.time-to-live=300000

# Scheduling
spring.task.scheduling.pool.size=2
```

## Business Logic

### Reward Credits
- Creates reward transactions with expiration dates
- Updates user balance immediately
- Supports configurable expiration periods (1-3650 days)

### Coin Redemption
- Validates sufficient balance before processing
- Creates redemption transactions for audit
- Uses FIFO (First-In-First-Out) strategy for coin consumption

### Expiry Management
- **Cache Update Job**: Runs every 10 minutes to update Redis with coins expiring in next 30 minutes
- **Expiry Processing Job**: Runs every hour to process expired rewards
- Automatic balance adjustments for expired coins

### Caching Strategy
- **User Balance**: 5-minute TTL (frequently changing)
- **Transaction History**: 15-minute TTL (more stable)
- **Expiry Information**: 15-minute TTL (updated by scheduled jobs)

## Error Handling

The application provides comprehensive error handling:

- `UserNotFoundException`: When user doesn't exist
- `InsufficientBalanceException`: When redemption exceeds balance
- `InvalidTransactionException`: For business rule violations
- Global exception handler for consistent API responses

## Monitoring & Health

- **Health Endpoint**: `/api/v1/health`
- **Actuator Endpoints**: Available at `/actuator`
- **Logging**: Structured logging with appropriate levels
- **Metrics**: Redis and database health checks

## Testing

### Sample API Calls

1. **Credit 100 coins to a user**:
   ```bash
   curl -X POST http://localhost:8080/api/v1/rewards \
     -H "userId: user123" \
     -H "Content-Type: application/json" \
     -d '{"numberOfCoins": 100, "expirationMinutes": 30}'
   ```

2. **Redeem 50 coins**:
   ```bash
   curl -X POST http://localhost:8080/api/v1/redeem \
     -H "userId: user123" \
     -H "Content-Type: application/json" \
     -d '{"numberOfCoins": 50}'
   ```

3. **Check balance**:
   ```bash
   curl -X GET http://localhost:8080/api/v1/view/coins \
     -H "userId: user123"
   ```

## Docker Compose Services

- **postgres**: PostgreSQL database with initialization scripts
- **redis**: Redis cache with persistent storage
- **app**: Spring Boot application (when built as Docker image)

## Security Considerations

- Input validation on all endpoints
- SQL injection protection via JPA/Hibernate
- Transaction-based operations for data consistency
- Proper exception handling to prevent information leakage

## Performance Optimizations

- Redis caching for frequently accessed data
- Database indexing on critical queries
- Connection pooling for database connections
- Scheduled batch processing for expiry management

## Contributing

1. Follow Java coding standards
2. Maintain comprehensive documentation
3. Include unit and integration tests
4. Update API documentation for any changes

## License

This project is licensed under the MIT License.
