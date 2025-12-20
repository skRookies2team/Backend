package com.story.game.story.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "story_choices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoice {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "choice_order")
    private Integer choiceOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private String tags;

    @Lob
    @Column(name = "immediate_reaction", columnDefinition = "TEXT")
    private String immediateReaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    private StoryNode sourceNode;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_node_id", referencedColumnName = "id")
    private StoryNode destinationNode;
}
