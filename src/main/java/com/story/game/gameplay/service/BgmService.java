package com.story.game.gameplay.service;

import com.story.game.ai.service.RelayServerClient;
import com.story.game.gameplay.dto.BgmDto;
import com.story.game.story.entity.StoryChoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * BGM Service with caching and pre-loading
 *
 * Features:
 * - Caching: Same node always gets same BGM (no duplicate API calls)
 * - Pre-loading: Pre-load BGM for next possible nodes in background
 * - Non-blocking: Pre-loading doesn't block game flow
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BgmService {

    private final RelayServerClient relayServerClient;

    // Cache: nodeId -> BGM
    private final ConcurrentHashMap<String, BgmDto> bgmCache = new ConcurrentHashMap<>();

    /**
     * Get BGM for a node (from cache or AI server)
     *
     * @param storyId Story ID
     * @param nodeId Node ID
     * @param nodeText Node text for BGM recommendation
     * @return BGM data (may be null if recommendation fails)
     */
    public BgmDto getBgmForNode(Long storyId, UUID nodeId, String nodeText) {
        String cacheKey = generateCacheKey(storyId, nodeId);

        // Check cache first
        BgmDto cachedBgm = bgmCache.get(cacheKey);
        if (cachedBgm != null) {
            log.debug("BGM cache hit for node: {} (mood={})", nodeId, cachedBgm.getMood());
            return cachedBgm;
        }

        // Cache miss - request from AI server
        log.info("BGM cache miss for node: {} - requesting from AI server", nodeId);
        BgmDto bgm = requestBgmFromAi(nodeText);

        if (bgm != null) {
            bgmCache.put(cacheKey, bgm);
            log.info("BGM cached for node: {} (mood={})", nodeId, bgm.getMood());
        } else {
            log.warn("Failed to get BGM for node: {}", nodeId);
        }

        return bgm;
    }

    /**
     * Pre-load BGM for next possible nodes (async, non-blocking)
     *
     * @param storyId Story ID
     * @param choices List of choices (next possible nodes)
     */
    @Async
    public CompletableFuture<Void> preloadNextNodesBgm(Long storyId, List<StoryChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            log.debug("No choices to preload BGM for");
            return CompletableFuture.completedFuture(null);
        }

        log.info("Pre-loading BGM for {} next nodes", choices.size());

        for (StoryChoice choice : choices) {
            if (choice.getDestinationNode() == null) {
                continue;
            }

            UUID nextNodeId = choice.getDestinationNode().getId();
            String nextNodeText = choice.getDestinationNode().getText();
            String cacheKey = generateCacheKey(storyId, nextNodeId);

            // Skip if already cached
            if (bgmCache.containsKey(cacheKey)) {
                log.debug("BGM already cached for next node: {}", nextNodeId);
                continue;
            }

            // Pre-load in background
            try {
                log.debug("Pre-loading BGM for next node: {}", nextNodeId);
                BgmDto bgm = requestBgmFromAi(nextNodeText);

                if (bgm != null) {
                    bgmCache.put(cacheKey, bgm);
                    log.info("BGM pre-loaded for next node: {} (mood={})", nextNodeId, bgm.getMood());
                } else {
                    log.warn("Failed to pre-load BGM for next node: {}", nextNodeId);
                }
            } catch (Exception e) {
                log.error("Error pre-loading BGM for next node: {}", nextNodeId, e);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Request BGM from AI server via relay server
     */
    private BgmDto requestBgmFromAi(String nodeText) {
        try {
            return relayServerClient.recommendMusic(nodeText);
        } catch (Exception e) {
            log.error("Failed to request BGM from AI server: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate cache key
     */
    private String generateCacheKey(Long storyId, UUID nodeId) {
        return "story:" + storyId + ":node:" + nodeId;
    }

    /**
     * Clear cache for a story (useful when story is deleted)
     */
    public void clearCacheForStory(Long storyId) {
        String prefix = "story:" + storyId + ":";
        bgmCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("Cleared BGM cache for story: {}", storyId);
    }

    /**
     * Clear all cache
     */
    public void clearAllCache() {
        int size = bgmCache.size();
        bgmCache.clear();
        log.info("Cleared all BGM cache ({} entries)", size);
    }

    /**
     * Get cache statistics
     */
    public int getCacheSize() {
        return bgmCache.size();
    }
}
