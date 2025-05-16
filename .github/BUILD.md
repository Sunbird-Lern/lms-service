# GitHub Action: Build and Deploy

## Overview
This GitHub Action automates the build and deployment process for the LMS service. It builds the project, packages it, and pushes a Docker image to GitHub Container Registry (GHCR).

## Usage
To use this action, ensure that your repository is configured to trigger on tag pushes. The action will automatically build and deploy your service whenever a new tag is pushed.

## Steps

1. **Set up JDK 11**: Configures the environment with JDK 11 using the `actions/setup-java` action.
2. **Checkout code**: Checks out the repository code using `actions/checkout`.
3. **Cache Maven packages**: Caches Maven dependencies to speed up the build process.
4. **Build and run test cases**:
   ```bash
   mvn clean install -DskipTests
   cd service
   mvn clean install
   ```
5. **Package build artifact**: Packages the application using Play framework.
   ```bash
   mvn -f service/pom.xml play2:dist
   ```
6. **Upload artifact**: Uploads the packaged artifact for later use.
7. **Extract image tag details**: Prepares Docker image name and tags based on the repository and commit details.
8. **Log in to GitHub Container Registry**: Authenticates to GHCR using the provided GitHub token.
9. **Build and push Docker image**: Builds the Docker image and pushes it to GHCR.

## Environment Variables
- `REGISTRY`: The GitHub Container Registry URL.

### How to Use the Docker Image

1. **Pull the Docker Image**: You can pull the Docker image from GHCR using the following command:
   ```bash
   docker pull ghcr.io/<repository-name>:<tag>
   ```
   Replace `<repository-name>`, and `<tag>` with the appropriate values.

2. **Initial Setup**: Follow the steps from [README.md](/README.md) to set up all the databases that are required for the application.

3. **Run the Docker Container**: After pulling the image, you can run it using:
   ```bash
   docker run -d -p <host-port>:<container-port> ghcr.io/<repository-name>:<tag>
   ```
   Replace `<host-port>` and `<container-port>` with the desired port numbers.

3. **Access the Application**: Once the container is running, you can access the application at `http://localhost:<host-port>`.