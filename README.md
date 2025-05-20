# Project Title

A brief description of what this project does.

## Overview

This project is a Java Spring Boot application built with Maven. It demonstrates the use of resilience features like circuit breaker and retry with the WebClient.

## Description
This code implements a resilient asynchronous HTTP client service using Spring Boot, Maven, and Resilience4j with reactive programming. It is composed of two main parts:

!1. The resilience configuration class sets up default registries for circuit breakers and retries. The retry configuration customizes the behavior by limiting attempts, setting wait durations, and targeting specific exceptions like IO and timeout errors.

!2. The HTTP client service class uses a WebClient to send HTTP requests asynchronously. It builds the request using details from an input holder, then processes the response. If error status codes (4\xx or 5\xx) occur, it reads the error message and wraps it in a RuntimeException. The service applies a timeout, along with circuit breaker and retry operators, ensuring that transient errors are handled gracefully. Logging and MDC usage provide traceability and contextual error logging, while error handling logic wraps different types of exceptions with custom messages.
Overall, this code provides a robust foundation for making resilient HTTP calls with proper error management and request recovery.

## Technologies

- Java
- Spring Boot
- Maven
- Resilience4j
- Reactive Programming

## Getting Started

### Prerequisites

- JDK 17 or later
- Maven 3.6 or later

### Installation

1. Clone the repository:
2. Build the project: mvn clean install
3. Running the Application

Run the Spring Boot application: mvn spring-boot:run

4. Testing

To execute the tests: mvn test
