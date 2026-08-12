package com.roottrace.knowledge.retrieval;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implements Reciprocal Rank Fusion (RRF) to combine semantic and keyword search results.
 *
 * RRF formula: score(d) = Σ 1 / (k + rank(d))
 *
 * where:
 *  - k is a smoothing constant (configurable, default 60)
 *  - rank(d) is the 1-based position of document d in each ranked list
 *
 * Chunks that appear in both lists receive contributions from both rankings.
 * Chunks only in one list receive a single contribution.
 * Final results are deduplicated by chunk ID and sorted by descending RRF score.
 *
 * This service is pure computation — no database or AI calls.
 */
@Service
public class ReciprocalRankFusionService {

    /**
     * Fuses two ranked lists using RRF.
     *
     * @param semanticResults  ranked list from semantic/vector search (best first)
     * @param keywordResults   ranked list from FTS/keyword search (best first)
     * @param rrfK             RRF smoothing constant (typically 60)
     * @param topK             maximum number of results to return
     * @return deduplicated, fused, ranked list of chunks
     */
    public List<RankedChunk> fuse(
            List<RetrievedChunk> semanticResults,
            List<RetrievedChunk> keywordResults,
            int rrfK,
            int topK) {

        // Accumulate RRF scores keyed by chunkId
        Map<UUID, double[]> rrfScores = new LinkedHashMap<>();
        // Track per-chunk metadata from whichever source provided it
        Map<UUID, RetrievedChunk> chunkMeta = new LinkedHashMap<>();
        // Track individual rank contributions
        Map<UUID, int[]> semanticRanks = new HashMap<>();
        Map<UUID, int[]> keywordRanks = new HashMap<>();

        // Process semantic results
        for (int i = 0; i < semanticResults.size(); i++) {
            RetrievedChunk chunk = semanticResults.get(i);
            UUID id = chunk.chunkId();
            int rank = i + 1; // 1-based
            rrfScores.computeIfAbsent(id, k -> new double[]{0.0})[0] += 1.0 / (rrfK + rank);
            semanticRanks.put(id, new int[]{rank});
            chunkMeta.put(id, chunk);
        }

        // Process keyword results
        for (int i = 0; i < keywordResults.size(); i++) {
            RetrievedChunk chunk = keywordResults.get(i);
            UUID id = chunk.chunkId();
            int rank = i + 1; // 1-based
            rrfScores.computeIfAbsent(id, k -> new double[]{0.0})[0] += 1.0 / (rrfK + rank);
            keywordRanks.put(id, new int[]{rank});
            // If chunk not already seen from semantic, add its metadata
            chunkMeta.putIfAbsent(id, chunk);
        }

        // Build RankedChunk list sorted by descending RRF score
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, double[]>comparingByValue(
                        Comparator.comparingDouble(arr -> -arr[0])))
                .limit(topK)
                .map(entry -> {
                    UUID id = entry.getKey();
                    double score = entry.getValue()[0];
                    RetrievedChunk meta = chunkMeta.get(id);
                    int semRank = semanticRanks.containsKey(id) ? semanticRanks.get(id)[0] : -1;
                    int kwRank  = keywordRanks.containsKey(id)  ? keywordRanks.get(id)[0]  : -1;
                    double semScore = semRank > 0 ? meta.score() : 0.0;
                    return new RankedChunk(
                            id,
                            meta.documentId(),
                            meta.documentTitle(),
                            meta.sectionPath(),
                            meta.content(),
                            semScore,
                            kwRank,
                            semRank,
                            score
                    );
                })
                .toList();
    }
}
