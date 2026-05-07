# CI/CD Integration

## GitHub Actions

```yaml
name: SpecSurge API Tests

on: [push, pull_request, schedule]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Build SpecSurge
        run: |
          cd specsurge
          mvn package
      
      - name: Run API Tests
        run: |
          java -jar specsurge/target/*.jar \
            --spec http://api:8082/v3/api-docs \
            --output ./reports
      
      - name: Upload Report
        uses: actions/upload-artifact@v3
        with:
          name: specsurge-report
          path: reports/*.html
      
      - name: Fail on Errors
        run: exit $? # Exits with 1 if crashes detected
```

---

## GitLab CI

```yaml
specsurge:
  image: maven:3.9-eclipse-temurin-21
  script:
    - cd specsurge && mvn package
    - java -jar target/*.jar --spec $API_URL/v3/api-docs
  artifacts:
    paths:
      - specsurge/test-reports/*.html
    expire_in: 7 days
```

---

## Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'cd specsurge && mvn package'
            }
        }
        
        stage('API Tests') {
            steps {
                sh '''
                    java -jar specsurge/target/*.jar \
                        --spec ${API_URL}/v3/api-docs \
                        --output ./reports
                '''
            }
        }
        
        stage('Publish Report') {
            steps {
                publishHTML([
                    reportDir: 'reports',
                    reportFiles: 'test-report-*.html',
                    reportName: 'SpecSurge API Tests'
                ])
            }
        }
    }
    
    post {
        failure {
            mail to: 'team@example.com',
                 subject: "API Tests Failed: ${env.JOB_NAME}",
                 body: "Check ${env.BUILD_URL}"
        }
    }
}
```

---

## CircleCI

```yaml
version: 2.1

jobs:
  api-tests:
    docker:
      - image: cimg/openjdk:21.0
    steps:
      - checkout
      - run:
          name: Build SpecSurge
          command: cd specsurge && mvn package
      - run:
          name: Run API Tests
          command: |
            java -jar specsurge/target/*.jar \
              --spec http://api:8082/v3/api-docs \
              --output ./reports
      - store_artifacts:
          path: reports
          destination: specsurge-reports

workflows:
  test:
    jobs:
      - api-tests
```

---

## Docker Compose (Full Stack Testing)

```yaml
version: '3.8'

services:
  api:
    image: myapp:latest
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=test
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 5s
      timeout: 3s
      retries: 10

  specsurge:
    image: maven:3.9-eclipse-temurin-21
    depends_on:
      api:
        condition: service_healthy
    volumes:
      - ./specsurge:/app
      - ./reports:/reports
    working_dir: /app
    command: >
      sh -c "mvn package &&
             java -jar target/*.jar 
             --spec http://api:8082/v3/api-docs 
             --base-url http://api:8082 
             --output /reports"
```

**Uso**:
```bash
docker-compose up --abort-on-container-exit
```

---

## Scheduled Testing (Cron)

### GitHub Actions (Weekly)

```yaml
on:
  schedule:
    - cron: '0 2 * * 1'  # Every Monday at 2am
```

### GitLab CI (Daily)

```yaml
specsurge-nightly:
  script:
    - java -jar specsurge/target/*.jar --spec $PROD_API_URL/v3/api-docs
  only:
    - schedules
```

---

## Exit Codes

SpecSurge returns:
- **0**: All tests passed (or only expected failures like 401/404)
- **1**: Real bugs detected (500s, unexpected errors)

**CI/CD Integration**: Fail the build on exit code 1.
