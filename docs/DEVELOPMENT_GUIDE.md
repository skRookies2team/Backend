# Development Guide

This guide provides setup instructions, development tips, and troubleshooting for the story-backend project.

## Local Development Setup

### Prerequisites
- **Java 17** or higher
- **MariaDB 10.x** or higher
- **Gradle** (wrapper included)
- **Git**
- **Python AI Server** (optional, for full functionality)

### Step-by-Step Setup

#### 1. Clone the Repository
```bash
git clone <repository-url>
cd story-backend
```

#### 2. Install MariaDB
**macOS** (via Homebrew):
```bash
brew install mariadb
brew services start mariadb
```

**Windows**:
- Download from https://mariadb.org/download/
- Install and start the service

**Linux** (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install mariadb-server
sudo systemctl start mariadb
```

#### 3. Create Database
```bash
# Login to MariaDB
mysql -u root -p

# Create database
CREATE DATABASE story_game;

# Create user (optional)
CREATE USER 'storyuser'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON story_game.* TO 'storyuser'@'localhost';
FLUSH PRIVILEGES;

# Exit
EXIT;
```

#### 4. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=story_game
DB_USERNAME=root
DB_PASSWORD=your_password

# AI Server
AI_SERVER_URL=http://localhost:8000
AI_SERVER_TIMEOUT=600000

# AWS S3
AWS_S3_BUCKET=your-bucket-name
AWS_S3_REGION=ap-northeast-2
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key

# JWT
JWT_SECRET=your-very-long-secret-key-at-least-256-bits-long
JWT_ACCESS_TOKEN_VALIDITY=3600000
JWT_REFRESH_TOKEN_VALIDITY=604800000

# Server
SERVER_PORT=8080

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# JPA
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true

# Logging
LOG_LEVEL=INFO
```

**Important**: Never commit `.env` to version control! It's in `.gitignore`.

#### 5. Build and Run

**Build the project**:
```bash
./gradlew build
```

**Run the application**:
```bash
./gradlew bootRun
```

**Run tests**:
```bash
./gradlew test
```

#### 6. Verify Setup

**Check server health**:
```bash
curl http://localhost:8080/api/health
```

**Check AI server connectivity** (if AI server is running):
```bash
curl http://localhost:8080/api/game/ai/health
```

**Access Swagger UI**:
```
http://localhost:8080/swagger-ui.html
```

## Development Workflow

### Running in Development Mode

**Auto-reload** (with Spring DevTools):
```bash
./gradlew bootRun
```
Changes to code will trigger automatic restart.

**Debug mode** (IntelliJ IDEA):
1. Click "Debug" button
2. Set breakpoints in code
3. Use debugger to inspect variables

**Debug mode** (Command line):
```bash
./gradlew bootRun --debug-jvm
```
Then attach debugger to port 5005.

### Database Management

**View database schema**:
```bash
mysql -u root -p story_game

SHOW TABLES;
DESCRIBE users;
DESCRIBE story_creation;
DESCRIBE game_sessions;
```

**Reset database** (development only):
```bash
# Drop and recreate
mysql -u root -p -e "DROP DATABASE story_game; CREATE DATABASE story_game;"

# Or set JPA_DDL_AUTO=create in .env (WARNING: destroys data)
```

**Check data**:
```bash
mysql -u root -p story_game

SELECT * FROM users;
SELECT id, title, status FROM story_creation;
SELECT * FROM game_sessions;
```

### Working with S3

**Local S3 alternative** (MinIO):
```bash
# Run MinIO locally
docker run -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"

# Update .env
AWS_S3_BUCKET=local-bucket
AWS_ACCESS_KEY=minioadmin
AWS_SECRET_KEY=minioadmin
```

**Test S3 connection**:
```bash
# Get pre-signed URL
curl http://localhost:8080/api/upload/presigned-url?fileKey=test.txt&contentType=text/plain
```

### Testing APIs

**Using Swagger UI**:
1. Go to `http://localhost:8080/swagger-ui.html`
2. Find endpoint to test
3. Click "Try it out"
4. Fill in parameters
5. Click "Execute"

**Using curl**:

```bash
# Register user
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'

# Use JWT token
TOKEN="<your-jwt-token>"

# Start game
curl -X POST http://localhost:8080/api/game/start \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "storyDataId": 1
  }'
```

**Using Postman/Insomnia**:
1. Import OpenAPI spec from `http://localhost:8080/v3/api-docs`
2. Set base URL to `http://localhost:8080`
3. Add JWT token to Authorization header

## Common Issues and Solutions

### Database Issues

#### Issue: Database connection failed
```
Error: Could not open JDBC Connection
```

**Solutions**:
1. Check MariaDB is running:
   ```bash
   # macOS
   brew services list

   # Linux
   sudo systemctl status mariadb

   # Windows
   services.msc (check MariaDB service)
   ```

2. Verify credentials in `.env`:
   ```bash
   DB_PASSWORD=your_password
   ```

3. Test connection manually:
   ```bash
   mysql -u root -p -h localhost
   ```

4. Check database exists:
   ```sql
   SHOW DATABASES;
   ```

#### Issue: Table doesn't exist
```
Error: Table 'story_game.users' doesn't exist
```

**Solution**:
- Set `JPA_DDL_AUTO=create` in `.env` (first run only)
- Or run migrations manually
- Then change back to `JPA_DDL_AUTO=update`

### AWS S3 Issues

#### Issue: S3 upload failed
```
Error: Access Denied (Service: Amazon S3; Status Code: 403)
```

**Solutions**:
1. Check AWS credentials in `.env`:
   ```bash
   AWS_ACCESS_KEY=your-key
   AWS_SECRET_KEY=your-secret
   ```

2. Verify bucket exists and region is correct:
   ```bash
   AWS_S3_BUCKET=your-bucket
   AWS_S3_REGION=ap-northeast-2
   ```

3. Check IAM permissions:
   - S3 bucket must allow `PutObject`, `GetObject`, `DeleteObject`
   - User must have permissions for these actions

4. Test with AWS CLI:
   ```bash
   aws s3 ls s3://your-bucket --region ap-northeast-2
   ```

### AI Server Issues

#### Issue: AI server timeout
```
Error: java.net.SocketTimeoutException: Read timed out
```

**Solutions**:
1. Check AI server is running:
   ```bash
   curl http://localhost:8000/health
   ```

2. Increase timeout in `application.yml`:
   ```yaml
   ai-server:
     timeout: 600000  # 10 minutes
   ```

3. Check network connectivity:
   ```bash
   ping localhost
   telnet localhost 8000
   ```

4. Verify `AI_SERVER_URL` in `.env`:
   ```bash
   AI_SERVER_URL=http://localhost:8000
   ```

### JWT Issues

#### Issue: JWT authentication failed
```
Error: 401 Unauthorized
```

**Solutions**:
1. Check JWT secret length (must be ≥256 bits / 32 characters):
   ```bash
   JWT_SECRET=your-very-long-secret-key-at-least-256-bits-long
   ```

2. Verify token in Authorization header:
   ```
   Authorization: Bearer <token>
   ```

3. Check token expiration:
   - Access token: 1 hour (default)
   - Refresh token: 7 days (default)

4. Use refresh token to get new access token:
   ```bash
   curl -X POST http://localhost:8080/api/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{"refreshToken": "<your-refresh-token>"}'
   ```

### Build Issues

#### Issue: Build failed with compilation error
```
Error: Could not find or load main class
```

**Solutions**:
1. Clean build:
   ```bash
   ./gradlew clean build
   ```

2. Clear Gradle cache:
   ```bash
   rm -rf ~/.gradle/caches
   ./gradlew build
   ```

3. Reimport in IDE:
   - IntelliJ: File → Invalidate Caches / Restart
   - Eclipse: Right-click project → Gradle → Refresh Gradle Project

#### Issue: Lombok not working
```
Error: Cannot resolve symbol 'builder'
```

**Solutions**:
1. Install Lombok plugin in IDE:
   - IntelliJ: Settings → Plugins → Search "Lombok" → Install
   - Eclipse: Download from https://projectlombok.org/

2. Enable annotation processing:
   - IntelliJ: Settings → Build → Compiler → Annotation Processors → Enable

3. Rebuild project

## Performance Optimization Tips

### Database Optimization

**1. Enable Query Logging** (development only):
```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

**2. Add Indexes** for frequently queried fields:
```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email")
})
public class User { }
```

**3. Use Pagination** for large result sets:
```java
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByUserIdOrderByCreatedAtDesc(
        Long userId,
        Pageable pageable
    );
}

// Usage
Pageable pageable = PageRequest.of(0, 20); // Page 0, 20 items
Page<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
```

### S3 Optimization

**1. Use Pre-signed URLs** for client-side uploads:
```java
// Generate pre-signed URL
String presignedUrl = s3Service.generatePresignedUrl(fileKey, contentType);

// Client uploads directly to S3 (doesn't go through backend)
```

**2. Set appropriate Content-Type**:
```java
ObjectMetadata metadata = new ObjectMetadata();
metadata.setContentType("application/json");
metadata.setContentLength(content.length());
s3Client.putObject(bucket, key, inputStream, metadata);
```

### Caching (Future Enhancement)

**Add Redis for session caching**:
```java
@Cacheable(value = "gameSessions", key = "#sessionId")
public GameSession getSession(String sessionId) {
    return gameSessionRepository.findById(sessionId)
        .orElseThrow(() -> new RuntimeException("Session not found"));
}
```

## Testing

### Unit Tests

**Test service methods**:
```java
@SpringBootTest
class GameServiceTest {

    @MockBean
    private GameSessionRepository gameSessionRepository;

    @MockBean
    private StoryDataRepository storyDataRepository;

    @Autowired
    private GameService gameService;

    @Test
    void testStartGame() {
        // Arrange
        StoryData storyData = StoryData.builder()
            .id(1L)
            .title("Test Story")
            .build();
        when(storyDataRepository.findById(1L))
            .thenReturn(Optional.of(storyData));

        // Act
        GameStateResponseDto result = gameService.startGame(1L);

        // Assert
        assertNotNull(result.getSessionId());
        assertEquals("Test Story", result.getEpisodeTitle());
    }
}
```

### Integration Tests

**Test API endpoints**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testStartGameEndpoint() throws Exception {
        mockMvc.perform(post("/api/game/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storyDataId\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").exists());
    }
}
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests GameServiceTest

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## IDE Configuration

### IntelliJ IDEA

**Recommended Plugins**:
- Lombok
- Spring Boot
- JPA Buddy
- SonarLint
- Rainbow Brackets

**Code Style**:
1. Settings → Editor → Code Style → Java
2. Import scheme: `Project` or `GoogleStyle`
3. Tab size: 4 spaces
4. Continuation indent: 8 spaces

**Run Configuration**:
1. Run → Edit Configurations
2. Add new "Spring Boot" configuration
3. Main class: `com.story.game.StoryGameApplication`
4. Environment variables: Load from `.env`

### VS Code

**Recommended Extensions**:
- Extension Pack for Java
- Spring Boot Extension Pack
- Lombok Annotations Support

**Settings** (`.vscode/settings.json`):
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.format.settings.url": "eclipse-formatter.xml",
  "java.saveActions.organizeImports": true
}
```

## Deployment

### Building for Production

**1. Update environment variables**:
```bash
# Production .env
JPA_DDL_AUTO=validate  # Never use 'create' or 'update' in production
JPA_SHOW_SQL=false
LOG_LEVEL=WARN
```

**2. Build JAR**:
```bash
./gradlew clean build -x test
```

**3. Run JAR**:
```bash
java -jar build/libs/story-game-1.0-SNAPSHOT.jar
```

### Docker Deployment

**Dockerfile**:
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build and run**:
```bash
docker build -t story-backend .
docker run -p 8080:8080 --env-file .env story-backend
```

## Useful Commands

### Gradle Commands
```bash
./gradlew tasks              # List all tasks
./gradlew dependencies       # Show dependencies
./gradlew bootRun            # Run application
./gradlew build              # Build project
./gradlew clean              # Clean build artifacts
./gradlew test               # Run tests
./gradlew bootJar            # Create executable JAR
```

### Database Commands
```bash
# Backup database
mysqldump -u root -p story_game > backup.sql

# Restore database
mysql -u root -p story_game < backup.sql

# Show table structure
mysql -u root -p -e "DESCRIBE story_game.users;"

# Count records
mysql -u root -p -e "SELECT COUNT(*) FROM story_game.game_sessions;"
```

### Git Commands
```bash
git status                   # Check status
git log --oneline           # View commit history
git checkout -b feature/xyz # Create new branch
git add .                   # Stage changes
git commit -m "message"     # Commit changes
git push origin feature/xyz # Push to remote
```

## Additional Resources

- **Spring Boot Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Spring Data JPA**: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- **Spring Security**: https://docs.spring.io/spring-security/reference/
- **JWT**: https://jwt.io/
- **AWS S3 Java SDK**: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
- **Lombok**: https://projectlombok.org/features/
- **Swagger/OpenAPI**: https://springdoc.org/
