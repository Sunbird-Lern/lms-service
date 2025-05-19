# PR Code Coverage

This document outlines the steps for the Pull Request Code Coverage process. 

## Triggering the Workflow
This GitHub Actions workflow is triggered automatically whenever a pull request (PR) is raised against any branch in the repository. It ensures that every proposed change is checked for code quality and coverage before being merged into the main codebase.

## Steps

1. **Checkout the Repository**
   - The first step is to checkout the repository to ensure you have the latest code.

2. **Set Up JDK 11**
   - JDK 11 is used for building the project. Ensure to use the `temurin` distribution and cache Maven dependencies.

3. **Build the Project and Generate Coverage Report**
   - Execute the following commands to clean and install the project while skipping tests:
     ```bash
     mvn clean install -DskipTests
     cd service
     mvn clean verify jacoco:report
     ```

4. **Set Up JDK 17**
   - After generating the coverage report, set up JDK 17 for further analysis.

5. **Run SonarQube Analysis**
   - Execute the SonarQube analysis to check the quality of the code and coverage. Make sure to provide the necessary project key, organization, and authentication token:
     ```bash
     mvn sonar:sonar \
       -Dsonar.projectKey=sunbird-lern \
       -Dsonar.organization=sunbird-lern \
       -Dsonar.host.url=[https://sonarcloud.io](https://sonarcloud.io) \
       -Dsonar.coverage.jacoco.xmlReportPaths=service/target/site/jacoco/jacoco.xml
     ```

## Notes
- Ensure that the `SONAR_TOKEN` secret is set in your GitHub repository for the SonarQube analysis to work.
- The coverage report can be found at `service/target/site/jacoco/jacoco.xml`.
