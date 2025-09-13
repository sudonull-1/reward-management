package com.rewardmanagement.reward_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the Reward Management System.
 * 
 * This Spring Boot application provides RESTful APIs for:
 * - Crediting reward coins to users
 * - Redeeming coins from user accounts  
 * - Viewing user balances and transaction history
 * - Automated expiry management for reward coins
 * 
 * The system uses:
 * - PostgreSQL for persistent data storage
 * - Redis for caching and session management
 * - Scheduled jobs for automated expiry processing
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
@EnableTransactionManagement
public class RewardManagementApplication {

	/**
	 * Main method to start the Spring Boot application.
	 * 
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(RewardManagementApplication.class, args);
	}
}
