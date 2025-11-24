package com.story.game.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GaugeSelectionRequestDto {

    @NotNull(message = "Selected gauge IDs are required")
    @Size(min = 2, max = 2, message = "Exactly 2 gauges must be selected")
    private List<String> selectedGaugeIds;
}
