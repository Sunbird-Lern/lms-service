# Build and Coverage Report

This document outlines the steps to build the project and generate the coverage report.

## Steps

1. **Checkout the Repository**
   - The first step is to checkout the repository to ensure you have the latest code.

2. **Set Up JDK 11**
   - We use JDK 11 for building the project. Make sure to use the `temurin` distribution and cache Maven dependencies.

3. **Build the Project and Generate Coverage Report**
   - Run the Maven command to clean and install the project while skipping tests.
   - Navigate to the `service` directory and generate the coverage report using JaCoCo.

4. **Set Up JDK 17**
   - After generating the coverage report, set up JDK 17 for further analysis.

5. **Run SonarQube Analysis**
   - Execute the SonarQube analysis to check the quality of the code and coverage. Make sure to provide the necessary project key, organization, and authentication token.

## Notes
- Ensure that the `SONAR_TOKEN` secret is set in your GitHub repository for the SonarQube analysis to work.
- The coverage report can be found at `service/target/site/jacoco/jacoco.xml`.
