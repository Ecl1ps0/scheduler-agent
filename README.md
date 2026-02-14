# Scheduler Agent Project

## Overview
The Scheduler Agent Project is a distributed system designed to manage and schedule Machine Learning model training tasks. It utilizes a Multi-Agent System (MAS) architecture where a central **Scheduler Agent** distributes workload (specifically ML model executables) to available **Worker Agents** ("model-trainer" services).

## Architecture
The system is built on a robust stack combining enterprise-grade Java frameworks with agent-based computing:

*   **Spring Boot**: Serves as the backbone application container and provides the REST API layer for file handling and triggers.
*   **JADE (Java Agent Development Framework)**: Powers the Multi-Agent System.
    *   **Scheduler Agent**: The core intelligence that monitors pending tasks (`.exe` models), discovers available Worker agents via Directory Facilitator (DF), and acts as a load balancer to distribute tasks.
    *   **Worker Interaction**: Workers communicate their workload status and receive task assignments via FIPA-compliant ACL messages.
*   **File Distribution**: The application acts as a file server, hosting model executables that are downloaded and executed by Workers.

## Tools and Libraries
The project leverages the following key technologies:

*   **Java 17**: Core programming language and runtime.
*   **Spring Boot 3.5.3**: Framework for web application and dependency injection.
    *   `spring-boot-starter-web`: REST API and static resource serving.
*   **JADE 4.5.0**: Middleware for creating and managing intelligent software agents.
*   **Apache POI 5.2.3**: Used for generating Excel reports (`poi-ooxml`) of training results.
*   **JNA (Java Native Access) 5.14.0**: For native library interaction if required.
*   **Maven**: Dependency management and build tool.

## Prerequisites
To run this project, you need:

*   **Java Development Kit (JDK) 17** or higher.
*   **Maven** (for building the project).

### For Non-Windows Environment (Linux / macOS)
This system distributes and creates processes for **Windows Executables (`.exe` files)** such as `catboost_model.exe`, `random_forest.exe`, and `xgboost.exe`.

> [!IMPORTANT]
> **Wine Requirement**: If you are running **Worker Agents** on **Linux** or **macOS**, you **MUST** have [Wine](https://www.winehq.org/) installed and configured. The generic system calls used to spawn these processes will fail on non-Windows operating systems without a compatibility layer like Wine to execute the `.exe` files.

## Build and Run

### 1. Build the Project
Compile the application and install dependencies using Maven:
```bash
mvn clean install
```

### 2. Run the Scheduler Agent
Start the Spring Boot application which hosts the Scheduler Agent:
```bash
mvn spring-boot:run
```

Once running, the Scheduler will:
1.  Initialize the JADE Main Container.
2.  Start the `Scheduler` agent (`com.prod_mas.agents.Scheduler`).
3.  Load the list of pending ML tasks (executable models).
4.  Periodically search for agents advertising the `model-trainer` service.
5.  Distribute tasks to available workers and track their completion.

## Output
*   **Console Logs**: Detailed real-time logs of task assignment, worker heartbeats, and completion status.
*   **Excel Report**: A `training_results.xlsx` file is generated/updated with performance metrics (execution time) for each task.