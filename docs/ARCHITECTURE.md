# Architecture Documentation

This document provides detailed architecture information about the story-backend project.

## Tech Stack

### Core Framework
- **Spring Boot**: 3.2.0
- **Java**: 17
- **Build Tool**: Gradle
- **Database**: MariaDB (JPA/Hibernate with MariaDBDialect)

### Spring Ecosystem
- **Spring Data JPA**: Entity persistence and repository pattern
- **Spring Web**: REST API endpoints
- **Spring WebFlux**: WebClient for async HTTP calls to AI server
- **Spring Security**: Authentication and authorization
- **Spring Validation**: Request validation with Jakarta Validation

### Authentication & Security
- **JWT**: JSON Web Tokens (jjwt 0.12.3)
  - Access token (1 hour validity)
  - Refresh token (7 days validity)
- **Spring Security**: Custom filter chain with JWT authentication
- **BCrypt**: Password hashing

### Cloud & Storage
- **AWS S3**: File upload/download with pre-signed URLs
  - Story files (novels, generated stories)
  - User media (profile images, post images/videos)
  - Analysis results
- **AWS SDK**: aws-java-sdk-s3 1.12.565

### API Documentation
- **Swagger/OpenAPI**: springdoc-openapi-starter-webmvc-ui 2.3.0
- Accessible at `/swagger-ui.html` when running
- Interactive API testing

### Utilities
- **Lombok**: Reduce boilerplate code
  - `@Builder`: Builder pattern
  - `@Slf4j`: Logging
  - `@RequiredArgsConstructor`: Constructor injection
  - `@Getter/@Setter`: Accessors
- **Jackson**: JSON serialization/deserialization
- **spring-dotenv**: Environment variable management from .env file

### Testing
- **JUnit 5**: Unit and integration testing
- **Spring Security Test**: Security testing utilities

## Package Structure

### Detailed Package Organization

```
com.story.game/
├── StoryGameApplication.java          # Main entry point (@SpringBootApplication)
│
├── achievement/                        # Achievement System
│   ├── dto/
│   │   └── AchievementDto.java
│   ├── entity/
│   │   ├── Achievement.java           # Achievement definitions
│   │   └── UserAchievement.java       # User progress tracking
│   ├── repository/
│   │   ├── AchievementRepository.java
│   │   └── UserAchievementRepository.java
│   └── service/
│       └── AchievementService.java
│
├── auth/                              # Authentication & Authorization
│   ├── controller/
│   │   └── AuthController.java        # /api/auth/* endpoints
│   ├── dto/
│   │   ├── LoginRequestDto.java
│   │   ├── SignUpRequestDto.java
│   │   ├── RefreshTokenRequestDto.java
│   │   └── TokenResponseDto.java
│   ├── entity/
│   │   ├── User.java                  # Implements UserDetails
│   │   └── RefreshToken.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── RefreshTokenRepository.java
│   ├── security/
│   │   ├── JwtTokenProvider.java      # JWT generation/validation
│   │   ├── JwtAuthenticationFilter.java  # Filter chain
│   │   └── CustomUserDetailsService.java
│   └── service/
│       └── AuthService.java           # Login, signup, token refresh
│
├── common/                            # Shared Components
│   ├── dto/
│   │   ├── FullStoryDto.java         # Complete story structure
│   │   ├── EpisodeDto.java           # Episode data
│   │   ├── StoryNodeDto.java         # Story tree nodes
│   │   ├── StoryChoiceDto.java       # Choice options
│   │   ├── CharacterDto.java         # Character info
│   │   ├── GaugeDto.java             # Gauge definitions
│   │   ├── EpisodeEndingDto.java     # Episode endings
│   │   └── FinalEndingDto.java       # Game endings
│   ├── entity/
│   │   └── StoryData.java            # Completed story metadata
│   ├── exception/
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│   └── repository/
│       └── StoryDataRepository.java
│
├── community/                         # Community Features
│   ├── controller/
│   │   ├── PostController.java       # /api/posts/*
│   │   ├── CommentController.java    # /api/comments/*
│   │   └── ReviewController.java     # /api/reviews/*
│   ├── dto/
│   │   ├── CreatePostRequestDto.java
│   │   ├── PostResponseDto.java
│   │   ├── CreateCommentRequestDto.java
│   │   ├── CommentResponseDto.java
│   │   ├── CreateReviewRequestDto.java
│   │   ├── ReviewResponseDto.java
│   │   ├── MediaDto.java
│   │   └── PostMediaUploadResponseDto.java
│   ├── entity/
│   │   ├── Post.java                 # User posts
│   │   ├── Comment.java              # Post comments
│   │   ├── StoryReview.java          # Story reviews
│   │   ├── Like.java                 # Like functionality
│   │   ├── Bookmark.java             # Bookmark posts
│   │   └── PostMedia.java            # Media attachments (S3)
│   ├── repository/
│   │   ├── PostRepository.java
│   │   ├── CommentRepository.java
│   │   ├── StoryReviewRepository.java
│   │   ├── LikeRepository.java
│   │   ├── BookmarkRepository.java
│   │   └── PostMediaRepository.java
│   └── service/
│       ├── PostService.java
│       ├── CommentService.java
│       └── ReviewService.java
│
├── creation/                          # Story Creation Pipeline
│   ├── controller/
│   │   ├── StoryManagementController.java  # /api/stories/*
│   │   └── UploadController.java           # /api/upload/*
│   ├── dto/
│   │   ├── StoryUploadRequestDto.java
│   │   ├── StoryUploadResponseDto.java
│   │   ├── S3UploadRequestDto.java
│   │   ├── StorySummaryResponseDto.java
│   │   ├── StoryCharactersResponseDto.java
│   │   ├── StoryGaugesResponseDto.java
│   │   ├── GaugeSelectionRequestDto.java
│   │   ├── GaugeSelectionResponseDto.java
│   │   ├── StoryConfigRequestDto.java
│   │   ├── StoryConfigResponseDto.java
│   │   ├── StoryGenerationStartResponseDto.java
│   │   ├── StoryProgressResponseDto.java
│   │   ├── StoryResultResponseDto.java
│   │   ├── PresignedUrlResponseDto.java
│   │   └── NovelAnalysisResponseDto.java
│   ├── entity/
│   │   └── StoryCreation.java        # Lifecycle tracking
│   ├── repository/
│   │   └── StoryCreationRepository.java
│   └── service/
│       └── StoryGenerationService.java  # AI integration
│
├── gameplay/                          # Game Play Engine
│   ├── controller/
│   │   └── GameController.java       # /api/game/*
│   ├── dto/
│   │   ├── StartGameRequestDto.java
│   │   ├── GameStateResponseDto.java
│   │   └── ChoiceRequestDto.java
│   ├── entity/
│   │   └── GameSession.java          # Game state tracking
│   ├── repository/
│   │   └── GameSessionRepository.java
│   └── service/
│       └── GameService.java          # Core game logic
│
├── infrastructure/                    # Technical Infrastructure
│   ├── config/
│   │   ├── WebConfig.java            # CORS configuration
│   │   ├── WebClientConfig.java      # AI server HTTP client
│   │   ├── SwaggerConfig.java        # API docs setup
│   │   ├── FileUploadProperties.java # File size limits
│   │   └── HealthCheckController.java
│   └── s3/
│       ├── S3Config.java             # AWS S3 setup
│       └── S3Service.java            # S3 operations
│
└── user/                             # User Profile Management
    ├── controller/
    │   └── UserController.java       # /api/users/*
    └── dto/
        ├── UserProfileDto.java
        ├── UpdateProfileRequestDto.java
        ├── ProfileImageUploadResponseDto.java
        └── GameHistoryDto.java
```

## Domain Breakdown

### 1. achievement - Achievement System
Manages user achievements and badges earned through gameplay.

**Key Entities**:
- `Achievement`: Achievement definitions (name, description, criteria)
- `UserAchievement`: User progress towards achievements

**Features**:
- Track gameplay milestones
- Award badges and rewards
- Progress tracking

### 2. auth - Authentication & Authorization
JWT-based authentication system with Spring Security integration.

**Key Entities**:
- `User`: User accounts (implements Spring Security's `UserDetails`)
- `RefreshToken`: Refresh token storage for token renewal

**Features**:
- User registration and login
- JWT access/refresh token generation
- Password encryption with BCrypt
- Role-based authorization (USER, ADMIN)

**Security Components**:
- `JwtTokenProvider`: Token generation and validation
- `JwtAuthenticationFilter`: Filter chain for JWT authentication
- `CustomUserDetailsService`: Load user details for Spring Security

### 3. common - Shared Components
Shared DTOs and entities used across multiple domains.

**Key DTOs**:
- `FullStoryDto`: Complete story structure with metadata
- `EpisodeDto`: Episode data with nodes and endings
- `StoryNodeDto`: Individual story nodes in the tree
- `StoryChoiceDto`: Choice options with tags
- `GaugeDto`, `CharacterDto`, `EpisodeEndingDto`, `FinalEndingDto`

**Key Entity**:
- `StoryData`: Completed story metadata (actual JSON stored in S3)

**Exception Handling**:
- `GlobalExceptionHandler`: Centralized exception handling with `@RestControllerAdvice`

### 4. community - Social Features
Community features including posts, comments, reviews, and interactions.

**Key Entities**:
- `Post`: User-generated posts with media attachments
- `Comment`: Comments on posts (supports nested replies)
- `StoryReview`: Story reviews with ratings
- `Like`: Like functionality for posts/comments
- `Bookmark`: Save posts for later
- `PostMedia`: Media attachments (images/videos in S3)

**Features**:
- Create posts with text and media (S3)
- Comment and reply system
- Story reviews and ratings
- Like and bookmark functionality
- Media upload via S3 pre-signed URLs

### 5. creation - Story Generation Pipeline
Multi-step story creation process with AI server integration.

**Key Entity**:
- `StoryCreation`: Tracks entire creation lifecycle

**Creation Statuses**:
1. `ANALYZING`: Novel analysis in progress
2. `SUMMARY_READY`: Summary completed
3. `CHARACTERS_READY`: Characters extracted
4. `GAUGES_READY`: Gauge suggestions ready
5. `GAUGES_SELECTED`: User selected 2 gauges
6. `CONFIGURED`: Generation settings configured
7. `GENERATING`: Story generation in progress
8. `COMPLETED`: Generation finished
9. `FAILED`: Generation failed

**Features**:
- Novel upload (direct text or S3 file)
- AI-powered analysis (summary, characters, gauges)
- User customization (gauge selection, episode count, depth)
- Asynchronous generation with progress tracking
- S3 storage for large files

### 6. gameplay - Game Play Engine
Core game logic for interactive story gameplay.

**Key Entity**:
- `GameSession`: Stores current game state
  - Current episode and node
  - Gauge states (Map<String, Integer>)
  - Accumulated tags (Map<String, Integer>)
  - Visited nodes (List<String>)
  - Completion status

**Game Logic**:
- Session initialization
- Node navigation (tree traversal)
- Choice processing and tag accumulation
- Episode ending evaluation based on tags
- Final ending determination based on gauges
- Condition evaluation (e.g., "hope >= 70 AND trust >= 60")

**Condition Evaluation**:
- Uses JavaScript ScriptEngine for dynamic condition parsing
- Supports AND/OR operators, comparison operators (>=, <=, >, <, ==)

### 7. infrastructure - Technical Infrastructure
Configuration and infrastructure services.

**Configuration Classes**:
- `WebConfig`: CORS setup for frontend origins
- `WebClientConfig`: AI server HTTP client (10-minute timeout)
- `SwaggerConfig`: API documentation setup
- `FileUploadProperties`: File size limits and allowed extensions

**S3 Service**:
- `S3Service`: AWS S3 integration
  - File upload/download
  - Pre-signed URL generation (client-side uploads)
  - Content retrieval for story JSON

### 8. user - User Profile Management
User profile and preferences management.

**Features**:
- Profile updates (nickname, bio)
- Profile image upload (S3)
- Game history tracking
- User statistics

## Database Schema

### Core Tables

**Authentication**:
- `users`: id, username, email, password, nickname, bio, profile_image_url, role, enabled
- `refresh_tokens`: id, token, user_id, expiry_date

**Story System**:
- `story_creation`: id, title, novel_text, s3_file_key, status, summary, characters_json, gauges_json, config...
- `story_data`: id, title, description, story_file_key (S3), total_episodes, total_nodes

**Game Play**:
- `game_sessions`: id, story_data_id, current_episode_id, current_node_id, gauge_states (JSON), accumulated_tags (JSON), visited_nodes (JSON), is_completed

**Community**:
- `posts`: id, user_id, title, content, media_urls, created_at
- `comments`: id, post_id, user_id, content, parent_comment_id, created_at
- `story_reviews`: id, story_data_id, user_id, rating, content, created_at
- `likes`: id, user_id, post_id, comment_id
- `bookmarks`: id, user_id, post_id
- `post_media`: id, post_id, media_type, s3_key, url

**Achievements**:
- `achievements`: id, name, description, criteria, icon_url
- `user_achievements`: id, user_id, achievement_id, progress, unlocked_at

### Key Relationships
- User (1) → (N) GameSession, Post, Comment, Review
- StoryData (1) → (N) GameSession, StoryReview
- Post (1) → (N) Comment, Like, Bookmark, PostMedia
- Comment (1) → (N) Like (polymorphic)

## AI Server Integration

### Communication Protocol
- **Protocol**: HTTP REST
- **Client**: Spring WebFlux WebClient
- **Timeout**: 10 minutes (600,000 ms)
- **Base URL**: Configurable via `AI_SERVER_URL` env variable

### AI Server Endpoints

**POST /analyze**
- Input: Novel text, title
- Output: Summary, characters, gauge suggestions
- Used in: Story creation step 1

**POST /generate**
- Input: Configuration (episodes, depth, gauges, endings)
- Output: Story generation job ID
- Used in: Story creation step 5

**GET /progress**
- Input: Job ID
- Output: Progress percentage, current phase, message
- Used in: Progress polling during generation

### Data Flow
1. User uploads novel → Backend sends to AI server `/analyze`
2. AI analyzes → Returns summary, characters, gauges
3. User configures → Backend sends to AI server `/generate`
4. AI generates story → Backend polls `/progress`
5. Generation complete → AI returns story JSON
6. Backend stores to S3 → Creates StoryData entity

## Performance Considerations

### S3 Storage Strategy
- **Large files in S3**: Story JSON files (can be >1MB) stored in S3, not DB
- **Database**: Only metadata and S3 file keys
- **On-demand loading**: `GameService` loads from S3 when needed
- **Future optimization**: Consider Redis caching for active games

### Session Management
- Sessions stored in database (persistent)
- UUIDs for session IDs (not sequential, more secure)
- Consider cleanup job for abandoned sessions (>30 days old)

### AI Server Calls
- Long timeout (10 minutes) for generation operations
- Asynchronous generation with progress polling
- Client-side polling interval: 3-5 seconds recommended
- Consider retry logic for transient failures

### File Upload
- **Pre-signed URLs**: Client uploads directly to S3 (reduces server load)
- **Size limits**:
  - Story files: 10MB
  - Images: 5MB
  - Videos: 100MB
- **Allowed formats**: Configured in `FileUploadProperties`
