# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test
```

## Project Overview

Interactive story game platform backend built with **Spring Boot 3.2 + JPA + MariaDB**.

Supports AI-powered interactive story generation, gameplay, user management, and community features.

**Main entry point**: `src/main/java/com/story/game/StoryGameApplication.java`

**Tech Stack**: Spring Boot 3.2, Java 17, MariaDB, JWT Auth, AWS S3, WebFlux (AI server integration)

## Architecture

### Package Structure
```
com.story.game/
├── achievement/      # Achievement system
├── auth/            # Authentication & Authorization (JWT)
├── common/          # Shared DTOs, entities, exceptions
├── community/       # Posts, comments, reviews, likes
├── creation/        # Story generation pipeline
├── gameplay/        # Game play engine
├── infrastructure/  # Config, S3 service
└── user/           # User profile management
```

### Layered Architecture
- **Controller**: REST endpoints (`@RestController`)
- **Service**: Business logic (`@Service`, `@Transactional`)
- **Repository**: Data access (`extends JpaRepository`)
- **Entity**: Database models (`@Entity`)
- **DTO**: Data transfer objects

## Core Flow

### Story Generation Flow
1. Novel upload → AI analysis (`StoryCreation` created, status: ANALYZING)
2. Summary/characters/gauges extracted (status: GAUGES_READY)
3. User selects 2 gauges (status: GAUGES_SELECTED)
4. Configuration: episodes, depth, endings (status: CONFIGURED)
5. Story generation via AI server (status: GENERATING, progress polling)
6. Completion → `StoryData` saved to S3 (status: COMPLETED)

### Game Play Flow
1. Player selects story → `GameSession` created
2. Make choices → Tags accumulated, navigate to next node
3. Reach leaf node → Episode ending evaluated, gauges updated
4. All episodes completed → Final ending determined

## Key Components

**Entities**:
- `User`: User accounts with JWT authentication
- `StoryCreation`: Tracks story generation lifecycle
- `StoryData`: Completed story metadata (JSON stored in S3)
- `GameSession`: Active game state (gauges, tags, current node)

**Services**:
- `StoryGenerationService`: AI server integration, manages creation pipeline
- `GameService`: Core game logic (navigation, endings, condition evaluation)
- `S3Service`: AWS S3 file upload/download

## REST API

### Story Generation (`/api/stories`)
```
POST /upload                  - Upload novel and start analysis
POST /upload-from-s3          - Upload from S3 file
GET  /{id}/summary            - Get summary
GET  /{id}/characters         - Get characters
GET  /{id}/gauges             - Get gauge suggestions
POST /{id}/gauges/select      - Select 2 gauges
POST /{id}/config             - Configure generation
POST /{id}/generate           - Start generation
GET  /{id}/progress           - Poll progress
GET  /{id}/result             - Get result preview
GET  /{id}/data               - Get full story data
```

### Game Play (`/api/game`)
```
POST /start                   - Start game
GET  /{sessionId}             - Get current state
POST /{sessionId}/choice      - Make choice
GET  /stories                 - List stories
GET  /stories/{id}/data       - Get story data
```

### Authentication (`/api/auth`)
```
POST /signup                  - Register user
POST /login                   - Login (returns JWT)
POST /refresh                 - Refresh access token
```

## Configuration

### Required Environment Variables
```bash
# Database
DB_PASSWORD=your_password

# AI Server
AI_SERVER_URL=http://localhost:8000

# AWS S3
AWS_S3_BUCKET=your-bucket-name
AWS_S3_REGION=ap-northeast-2
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key

# JWT
JWT_SECRET=your-secret-key-at-least-32-characters
```

See `docs/ENV_SETUP.md` for detailed environment setup.

## Coding Conventions

**Key Patterns**:
- Constructor injection: `@RequiredArgsConstructor`
- Builder pattern: `@Builder` for DTOs
- Logging: `@Slf4j` with structured logging
- Transactions: `@Transactional` on service methods
- Validation: `@Valid` on request DTOs

**Naming**:
- Entities: `User`, `StoryCreation`, `GameSession`
- DTOs: `LoginRequestDto`, `GameStateResponseDto`
- Services: `AuthService`, `GameService`

See `docs/CODING_CONVENTIONS.md` for detailed conventions.

## Python AI Integration

Integrates with Python AI engine: https://github.com/skRookies2team/AI/tree/feature/kwak

- Protocol: HTTP REST via WebFlux WebClient
- Timeout: 10 minutes
- Endpoints: `/analyze`, `/generate`, `/progress`

## Related Documentation

- **Architecture Details**: `docs/ARCHITECTURE.md` - Tech stack, package structure
- **Coding Conventions**: `docs/CODING_CONVENTIONS.md` - Detailed coding standards
- **Development Guide**: `docs/DEVELOPMENT_GUIDE.md` - Setup, tips, troubleshooting
- **API Specification**: `docs/STORY_GENERATION_API.md` - Story creation API details
- **Frontend Integration**: `docs/FRONTEND_INTEGRATION_GUIDE.md`
- **Image Generation Flow**: `docs/IMAGE_GENERATION_FLOW.md`
- **Environment Setup**: `docs/ENV_SETUP.md`
