#!/bin/bash

# Reward Management System Startup Script

echo "🚀 Starting Reward Management System..."
echo "================================================"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose and try again."
    exit 1
fi

echo "🔧 Starting infrastructure services..."
docker-compose up -d postgres redis

echo "⏳ Waiting for services to be ready..."
sleep 10

# Check if PostgreSQL is ready
echo "🔍 Checking PostgreSQL connection..."
until docker-compose exec postgres pg_isready -U reward_user -d reward_management > /dev/null 2>&1; do
    echo "⏳ Waiting for PostgreSQL to be ready..."
    sleep 2
done
echo "✅ PostgreSQL is ready!"

# Check if Redis is ready
echo "🔍 Checking Redis connection..."
until docker-compose exec redis redis-cli ping > /dev/null 2>&1; do
    echo "⏳ Waiting for Redis to be ready..."
    sleep 2
done
echo "✅ Redis is ready!"

echo "🏗️  Building and starting the application..."
if command -v ./mvnw &> /dev/null; then
    ./mvnw spring-boot:run
else
    echo "🐳 Using Docker to run the application..."
    # You can uncomment the next line if you build a Docker image for the app
    # docker-compose up app
    echo "Please run: ./mvnw spring-boot:run"
fi

echo "🎉 Reward Management System is starting up!"
echo "📚 API Documentation: http://localhost:8080/api/v1/health"
echo "🔍 Health Check: http://localhost:8080/actuator/health"
