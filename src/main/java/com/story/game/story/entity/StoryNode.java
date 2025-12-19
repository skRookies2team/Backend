package com.story.game.story.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "story_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryNode {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    // The choice that led to this node. For the root node of an episode, this will be null.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_choice_id")
    private StoryChoice parentChoice;

    @OneToMany(mappedBy = "sourceNode", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoryChoice> outgoingChoices = new ArrayList<>();

    private Integer depth;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "node_type")
    private String nodeType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String situation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "npc_emotions", columnDefinition = "TEXT")
    private String npcEmotions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relations_update", columnDefinition = "TEXT")
    private String relationsUpdate;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_file_key", length = 500)
    private String imageFileKey;
}
