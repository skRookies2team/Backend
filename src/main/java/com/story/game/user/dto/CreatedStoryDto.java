package com.story.game.user.dto;

import com.story.game.creation.entity.StoryCreation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatedStoryDto {
    private String storyId;
    private String title;
    private String genre;
    private String description;
    private StoryCreation.CreationStatus status;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private Integer totalEpisodes;
    private Integer completedEpisodes;
    private Integer progressPercentage;
    private Long likesCount;
    private Long viewCount;

    public static CreatedStoryDto from(StoryCreation storyCreation, String thumbnailUrl, Long likesCount, Long viewCount) {
        return CreatedStoryDto.builder()
                .storyId(storyCreation.getId())
                .title(storyCreation.getTitle())
                .genre(storyCreation.getGenre())
                .description(storyCreation.getDescription())
                .status(storyCreation.getStatus())
                .thumbnailUrl(thumbnailUrl)
                .createdAt(storyCreation.getCreatedAt())
                .totalEpisodes(storyCreation.getTotalEpisodesToGenerate())
                .completedEpisodes(storyCreation.getCompletedEpisodes())
                .progressPercentage(storyCreation.getProgressPercentage())
                .likesCount(likesCount)
                .viewCount(viewCount)
                .build();
    }
}
