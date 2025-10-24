#!/bin/bash
# Development reload script for LDAP Browser
# Runs the application in development mode with Spring Boot DevTools

echo "Starting LDAP Browser in development mode..."
echo "Application will be available at http://localhost:8080"
echo "Press Ctrl+C to stop"
echo ""

mvn spring-boot:run -Dspring-boot.run.profiles=development
