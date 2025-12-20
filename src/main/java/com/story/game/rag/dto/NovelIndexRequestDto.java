package com.story.game.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelIndexRequestDto {

    @NotBlank(message = "Story ID is required")
    @Size(max = 100)
    @JsonProperty("story_id")
    private String storyId;

    @NotBlank(message = "Title is required")
    @Size(max = 500)
    @JsonProperty("title")
    private String title;

    @NotBlank(message = "File key is required")
    @JsonProperty("file_key")
    private String fileKey;

    @NotBlank(message = "Bucket is required")
    @JsonProperty("bucket")
    private String bucket;
}
