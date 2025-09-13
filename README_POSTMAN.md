# Postman Collection for Reward Management API

This directory contains Postman collection and environment files for comprehensive API testing of the Reward Management System.

## Files Included

1. **Reward_Management_API.postman_collection.json** - Main collection with all API endpoints
2. **Reward_Management_Environment.postman_environment.json** - Environment variables for easy configuration

## How to Import

### Step 1: Import Collection
1. Open Postman
2. Click **Import** button (top left)
3. Select **Upload Files** tab
4. Choose `Reward_Management_API.postman_collection.json`
5. Click **Import**

### Step 2: Import Environment
1. Click **Import** again
2. Select **Upload Files** tab
3. Choose `Reward_Management_Environment.postman_environment.json`
4. Click **Import**

### Step 3: Set Environment
1. Select **Reward Management - Local** from the environment dropdown (top right)
2. The collection will now use the configured base URL and user ID

## Collection Structure

### 🏥 Basic Operations
- **Health Check** - Verify service is running
- **Credit Rewards** - Award coins to users
- **Get Current Balance** - Quick balance check
- **View Coins & History** - Complete user information
- **Redeem Coins** - Spend user coins

### 🧪 Test Scenarios
- **Credit Large Reward** - Test with larger amounts
- **Test Insufficient Balance Error** - Error handling for redemption
- **Test Invalid Input Error** - Validation error testing
- **Test Missing Header Error** - Required header validation

## Environment Variables

| Variable | Default Value | Description |
|----------|---------------|-------------|
| `baseUrl` | `http://localhost:8080/api/v1` | API base URL |
| `userId` | `testuser123` | Default user ID for testing |
| `alternativeUserId` | `demouser456` | Alternative user for multi-user tests |

## Testing Workflows

### Complete User Journey
Run these requests in sequence for a complete test:

1. **Health Check** - Verify service is running
2. **Credit Rewards** - Award initial coins
3. **Get Current Balance** - Verify balance
4. **Credit Large Reward** - Add more coins
5. **View Coins & History** - See complete transaction history
6. **Redeem Coins** - Spend some coins
7. **Get Current Balance** - Verify final balance

### Error Testing
Test error scenarios:

1. **Test Invalid Input Error** - Negative coin amounts
2. **Test Missing Header Error** - Missing userId header
3. **Test Insufficient Balance Error** - Redeem more than available

## Automatic Testing

Each request includes automatic tests that verify:
- ✅ HTTP status codes
- ✅ Response structure
- ✅ Business logic validation
- ✅ Error handling

## Customization

### Change User ID
1. Go to **Environments** tab
2. Select **Reward Management - Local**
3. Update `userId` or `alternativeUserId` values
4. Save the environment

### Change Base URL
Update the `baseUrl` variable to test against different environments:
- Local: `http://localhost:8080/api/v1`
- Staging: `https://staging.yourcompany.com/api/v1`
- Production: `https://api.yourcompany.com/api/v1`

## Request Examples

### Credit Rewards
```json
{
  "numberOfCoins": 100,
  "expirationMinutes": 30
}
```

### Redeem Coins
```json
{
  "numberOfCoins": 25
}
```

## Expected Responses

### Successful Response
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { /* response data */ },
  "errorCode": null,
  "timestamp": "2024-09-13T10:30:45.123"
}
```

### Error Response
```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "errorCode": "ERROR_CODE",
  "timestamp": "2024-09-13T10:30:45.123"
}
```

## Running Tests

1. **Single Request**: Click **Send** on any request
2. **Collection Runner**: 
   - Click **Runner** tab
   - Select **Reward Management API** collection
   - Choose **Reward Management - Local** environment
   - Click **Run Reward Management API**

## Troubleshooting

### Service Not Running
If you get connection errors:
```bash
# Start the services
docker-compose up -d
./mvnw spring-boot:run
```

### Wrong Port
Verify the application is running on port 8080:
```bash
curl http://localhost:8080/api/v1/health
```

### Environment Not Set
Make sure **Reward Management - Local** is selected in the environment dropdown.

## Support

For issues or questions about the API collection, refer to the main project README.md or check the application logs.
