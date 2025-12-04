package com.story.game.story.entity;

import com.story.game.creation.entity.StoryCreation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "episodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Episode {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_creation_id", nullable = false)
    private StoryCreation story;

    @Column(nullable = false)
    private String title;

    @Column(name = "episode_order")
    private Integer order;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String theme;

    @Lob
    @Column(name = "intro_text", columnDefinition = "TEXT")
    private String introText;

    @OneToMany(mappedBy = "episode", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StoryNode> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "episode", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EpisodeEnding> endings = new ArrayList<>();
}
