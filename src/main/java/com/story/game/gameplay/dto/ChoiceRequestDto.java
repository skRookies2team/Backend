package com.story.game.gameplay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChoiceRequestDto {
    @NotNull(message = "Choice index is required")
    @Min(value = 0, message = "Choice index must be non-negative")
    private Integer choiceIndex;
}
