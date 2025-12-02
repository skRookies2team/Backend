# Coding Conventions

This document defines coding standards and best practices for the story-backend project.

## General Principles

### 1. Layered Architecture
Maintain strict separation of concerns across layers:

```
Controller → Service → Repository → Database
     ↓          ↓
    DTO      Entity
```

**Layer Responsibilities**:
- **Controller**: HTTP handling, request/response mapping, validation
- **Service**: Business logic, transaction management, orchestration
- **Repository**: Data access, query methods
- **Entity**: Database model, JPA annotations
- **DTO**: Data transfer between layers, API contracts

### 2. Dependency Injection
Always use **constructor injection** with Lombok's `@RequiredArgsConstructor`:

```java
@Service
@RequiredArgsConstructor
public class GameService {
    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;
    private final ObjectMapper objectMapper;
}
```

**Why constructor injection?**
- Immutability (final fields)
- Easier testing (can pass mocks in constructor)
- Explicit dependencies

### 3. Immutability
Prefer immutable objects where possible:

```java
@Getter
@Builder
public class GameStateResponseDto {
    private final String sessionId;
    private final String currentNodeId;
    private final Map<String, Integer> gaugeStates;
    private final List<StoryChoiceDto> choices;
}
```

## Naming Conventions

### Classes

**Entities** - Singular nouns:
```java
User.java
StoryCreation.java
GameSession.java
Post.java
```

**DTOs** - Purpose + `Dto` suffix:
```java
LoginRequestDto.java          // Request
GameStateResponseDto.java     // Response
StoryUploadRequestDto.java    // Request
```

**Controllers** - Domain + `Controller`:
```java
AuthController.java
GameController.java
StoryManagementController.java
```

**Services** - Domain + `Service`:
```java
AuthService.java
GameService.java
StoryGenerationService.java
```

**Repositories** - Entity + `Repository`:
```java
UserRepository.java
StoryDataRepository.java
GameSessionRepository.java
```

### Methods

**Controllers** - HTTP verb + resource:
```java
@PostMapping("/login")
public ResponseEntity<TokenResponseDto> login(@RequestBody LoginRequestDto request)

@GetMapping("/{sessionId}")
public ResponseEntity<GameStateResponseDto> getGameState(@PathVariable String sessionId)

@PostMapping("/{sessionId}/choice")
public ResponseEntity<GameStateResponseDto> makeChoice(
    @PathVariable String sessionId,
    @RequestBody ChoiceRequestDto request)
```

**Services** - Action verb + noun:
```java
public GameStateResponseDto startGame(Long storyDataId)
public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex)
public StoryData saveStoryData(String title, String description, String storyJson)
public List<StoryData> getAllStories()
```

**Repositories** - Spring Data JPA conventions:
```java
Optional<User> findByUsername(String username)
List<Post> findByUserIdOrderByCreatedAtDesc(Long userId)
boolean existsByEmail(String email)
```

### Variables

**camelCase** for variables and parameters:
```java
String sessionId;
Integer choiceIndex;
Map<String, Integer> gaugeStates;
List<String> visitedNodes;
```

**UPPER_SNAKE_CASE** for constants:
```java
private static final int DEFAULT_GAUGE_VALUE = 50;
private static final String STORY_FILE_PREFIX = "stories/";
```

## Annotations

### Entity Classes
```java
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    // ...
}
```

### Controller Classes
```java
@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Game Play", description = "게임 플레이 관련 API")
public class GameController {

    private final GameService gameService;

    @PostMapping("/start")
    @Operation(summary = "게임 시작", description = "...")
    public ResponseEntity<GameStateResponseDto> startGame(
            @Valid @RequestBody StartGameRequestDto request) {
        // ...
    }
}
```

### Service Classes
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameSessionRepository gameSessionRepository;

    @Transactional
    public GameStateResponseDto startGame(Long storyDataId) {
        // ...
    }

    @Transactional(readOnly = true)
    public GameStateResponseDto getGameState(String sessionId) {
        // ...
    }
}
```

### DTO Classes
```java
@Getter
@Builder
public class GameStateResponseDto {
    private String sessionId;
    private String currentEpisodeId;
    private String currentNodeId;
    private Map<String, Integer> gaugeStates;
    private List<StoryChoiceDto> choices;
}

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceRequestDto {
    @NotNull(message = "Choice index is required")
    @Min(value = 0, message = "Choice index must be non-negative")
    private Integer choiceIndex;
}
```

## Transaction Management

### Use @Transactional Appropriately

**Read-only transactions**:
```java
@Transactional(readOnly = true)
public GameStateResponseDto getGameState(String sessionId) {
    // No database modifications
}

@Transactional(readOnly = true)
public List<StoryData> getAllStories() {
    return storyDataRepository.findAll();
}
```

**Write transactions**:
```java
@Transactional
public GameStateResponseDto startGame(Long storyDataId) {
    // Creates new GameSession
    GameSession session = GameSession.builder()
        .storyDataId(storyDataId)
        // ...
        .build();
    session = gameSessionRepository.save(session);
    return buildResponse(session);
}

@Transactional
public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) {
    // Modifies GameSession state
    GameSession session = gameSessionRepository.findById(sessionId)
        .orElseThrow(() -> new RuntimeException("Session not found"));

    // Update session state
    session.setCurrentNodeId(nextNode.getId());
    session = gameSessionRepository.save(session);

    return buildResponse(session);
}
```

**Transaction boundaries**:
- Apply `@Transactional` at **service layer**, not controller
- One transaction per use case/business operation
- Keep transactions as short as possible

## Error Handling

### Exception Handling Strategy

**Global Exception Handler**:
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(message));
    }
}
```

**Service Layer Exceptions**:
```java
// Throw descriptive RuntimeExceptions
public GameSession findSessionOrThrow(String sessionId) {
    return gameSessionRepository.findById(sessionId)
        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
}

// Validate business rules
if (session.getIsCompleted()) {
    throw new RuntimeException("Game is already completed");
}

if (choiceIndex >= currentNode.getChoices().size()) {
    throw new RuntimeException("Invalid choice index: " + choiceIndex);
}
```

## Logging

### Logging Levels

**INFO** - Important business events:
```java
log.info("=== Upload Novel Request ===");
log.info("Title: {}", request.getTitle());
log.info("Novel uploaded. StoryId: {}", response.getStoryId());
log.info("Game started for storyDataId: {}, sessionId: {}", storyDataId, sessionId);
```

**DEBUG** - Detailed flow information:
```java
log.debug("=== Get Progress Request ===");
log.debug("StoryId: {}", storyId);
log.debug("Current node: {}, depth: {}", nodeId, depth);
```

**WARN** - Potential issues:
```java
log.warn("Failed to evaluate condition: {}", condition, e);
log.warn("AI server timeout, retrying...");
```

**ERROR** - Errors and exceptions:
```java
log.error("Failed to parse story JSON", e);
log.error("S3 upload failed for key: {}", fileKey, e);
```

### Logging Patterns

**Controller logging**:
```java
@PostMapping("/{storyId}/generate")
public ResponseEntity<StoryGenerationStartResponseDto> startGeneration(
        @PathVariable String storyId) {
    log.info("=== Start Generation Request ===");
    log.info("StoryId: {}", storyId);

    StoryGenerationStartResponseDto response = storyManagementService.startGeneration(storyId);

    log.info("Story generation started");
    return ResponseEntity.ok(response);
}
```

**Service logging**:
```java
@Transactional
public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) {
    log.debug("Processing choice {} for session {}", choiceIndex, sessionId);

    GameSession session = findSessionOrThrow(sessionId);
    StoryNodeDto currentNode = getCurrentNode(session);

    log.debug("Current node: {}, available choices: {}",
        currentNode.getId(), currentNode.getChoices().size());

    // ... business logic ...

    log.info("Choice processed successfully. New node: {}", nextNode.getId());
    return buildResponse(session);
}
```

## Validation

### Request Validation

**Use Jakarta Validation annotations**:
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

**Controller validation**:
```java
@PostMapping("/login")
public ResponseEntity<TokenResponseDto> login(
        @Valid @RequestBody LoginRequestDto request) {
    // @Valid triggers validation
}
```

**Custom validation**:
```java
// In service layer
public void validateGaugeSelection(List<String> selectedGaugeIds) {
    if (selectedGaugeIds == null || selectedGaugeIds.size() != 2) {
        throw new RuntimeException("Must select exactly 2 gauges");
    }
}
```

## JSON Handling

### Entity JSON Fields

**Store complex data as JSON in TEXT columns**:
```java
@Entity
public class StoryCreation {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characters_json", columnDefinition = "TEXT")
    private String charactersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gauges_json", columnDefinition = "TEXT")
    private String gaugesJson;
}
```

**Parse JSON with ObjectMapper**:
```java
@Service
@RequiredArgsConstructor
public class GameService {
    private final ObjectMapper objectMapper;

    private FullStoryDto parseStoryJson(String json) {
        try {
            return objectMapper.readValue(json, FullStoryDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse story JSON", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
```

## API Documentation

### Swagger/OpenAPI Annotations

**Controller level**:
```java
@Tag(name = "Story Management", description = "스토리 생성 관리 API")
@RestController
@RequestMapping("/api/stories")
public class StoryManagementController {
    // ...
}
```

**Method level**:
```java
@PostMapping("/upload")
@Operation(
    summary = "소설 업로드 및 분석 시작",
    description = "소설 텍스트를 업로드하고 AI 분석을 시작합니다. " +
                  "백그라운드에서 요약, 캐릭터, 게이지 추출이 진행됩니다."
)
public ResponseEntity<StoryUploadResponseDto> uploadNovel(
        @Valid @RequestBody StoryUploadRequestDto request) {
    // ...
}
```

## Code Organization

### File Structure
```
Controller.java
├── Class-level annotations
├── Dependencies (injected via constructor)
├── Endpoints (grouped by functionality)
└── Helper methods (private)
```

### Method Organization
```java
@Service
@RequiredArgsConstructor
public class GameService {

    // 1. Dependencies
    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;

    // 2. Public API methods
    @Transactional
    public GameStateResponseDto startGame(Long storyDataId) { }

    @Transactional(readOnly = true)
    public GameStateResponseDto getGameState(String sessionId) { }

    @Transactional
    public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) { }

    // 3. Private helper methods
    private GameStateResponseDto buildResponse(GameSession session, ...) { }

    private StoryNodeDto findNodeById(EpisodeDto episode, String nodeId) { }

    private boolean evaluateCondition(String condition, Map<String, Integer> values) { }
}
```

## Best Practices

### 1. Don't Repeat Yourself (DRY)
Extract common logic into helper methods or utility classes.

### 2. Single Responsibility Principle
Each class/method should have one clear purpose.

### 3. Explicit Over Implicit
Make code intentions clear through naming and structure.

### 4. Fail Fast
Validate inputs early and throw exceptions immediately.

### 5. Use Optional Wisely
```java
// Good: Clear intent
public User findUserOrThrow(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found: " + userId));
}

// Use Optional when null is a valid state
public Optional<RefreshToken> findRefreshToken(String token) {
    return refreshTokenRepository.findByToken(token);
}
```

### 6. Keep Controllers Thin
Business logic belongs in services, not controllers.

### 7. Test-Friendly Code
Design for testability - use dependency injection, avoid static methods.

## Anti-Patterns to Avoid

❌ **Field injection**:
```java
@Autowired
private GameService gameService; // Don't do this
```

✅ **Constructor injection**:
```java
private final GameService gameService; // Do this with @RequiredArgsConstructor
```

❌ **Business logic in controllers**:
```java
@PostMapping("/choice")
public ResponseEntity<?> makeChoice(...) {
    // Complex business logic here - BAD
}
```

✅ **Delegate to services**:
```java
@PostMapping("/choice")
public ResponseEntity<?> makeChoice(...) {
    return ResponseEntity.ok(gameService.makeChoice(sessionId, choiceIndex));
}
```

❌ **Catching generic exceptions without logging**:
```java
try {
    // ...
} catch (Exception e) {
    return null; // BAD: Swallows errors
}
```

✅ **Proper exception handling**:
```java
try {
    // ...
} catch (Exception e) {
    log.error("Operation failed", e);
    throw new RuntimeException("Failed to process request", e);
}
```
