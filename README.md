This is a placeholder app for our project E-commerce Basket and RFM Patters

Elements from the previous YATDL project were reused; however, all additional files and folders not required for the application to run were removed to start the project from a clean base.

Project runs using the same command
docker compose up --build

Gradle was used as the build tool for the Java/Spring Boot backend. Existing Gradle build configuration from the earlier YATDL project was reused to ensure consistency, and none necessary files such as the controllers, notes and directories were removed to start project from scratch.

NPM is used as the build tool for the React/TypeScript frontend. Within the frontend src folder, the components, services, and types directories exist as part of the planned project structure, but currently contain no implementation. The files App.tsx, index.css, index.tsx, and index.html exist to support the placeholder application.

Database connected using Docker Compose-managed MYSQL/MaraDB serviced used by the Spring Boot backend

Poetry for python has not been implimented