package com.roottrace.knowledge.retrieval;

import com.roottrace.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates hybrid knowledge retrieval: FTS + semantic search + RRF fusion.
 *
 * Graceful degradation:
 *  - If semantic search fails but FTS succeeds → use FTS results only
 *  - If FTS fails but semantic succeeds → use semantic results only
 *  - If both fail → throw RetrievalException
 */
@Service
public class HybridRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final SemanticSearchService semanticSearchService;
    private final FullTextSearchService fullTextSearchService;
    private final ReciprocalRankFusionService rrfService;
    private final AiProperties aiProperties;

    public HybridRetrievalService(
            @Autowired(required = false) SemanticSearchService semanticSearchService,
            FullTextSearchService fullTextSearchService,
            ReciprocalRankFusionService rrfService,
            AiProperties aiProperties) {
        this.semanticSearchService = semanticSearchService;
        this.fullTextSearchService = fullTextSearchService;
        this.rrfService = rrfService;
        this.aiProperties = aiProperties;
    }

    /**
     * Performs hybrid retrieval for the given query.
     * The embedding call (inside SemanticSearchService) MUST be called outside any transaction.
     */
    public RetrievalResult retrieve(RetrievalQuery query) {
        AiProperties.Retrieval cfg = aiProperties.getRetrieval();
        int effectiveTopK = query.topK() > 0 ? query.topK() : cfg.getTopK();
        int semTopK = cfg.getSemanticTopK();
        int kwTopK  = cfg.getKeywordTopK();

        logger.info("Hybrid retrieval started: query='{}', topK={}", query.query(), effectiveTopK);

        List<RetrievedChunk> semanticResults = null;
        List<RetrievedChunk> keywordResults = null;
        boolean semanticAvailable = false;
        boolean keywordAvailable = false;
        Exception semanticFailure = null;
        Exception keywordFailure = null;

        // Attempt semantic search
        if (semanticSearchService != null) {
            try {
                semanticResults = semanticSearchService.search(query.query(), semTopK);
                semanticAvailable = true;
                logger.debug("Semantic search returned {} results", semanticResults.size());
            } catch (Exception e) {
                semanticFailure = e;
                logger.warn("Semantic search failed, will degrade to FTS-only: {}", e.getMessage());
            }
        } else {
            logger.warn("SemanticSearchService unavailable (AI not configured), using FTS only");
        }

        // Attempt FTS search
        try {
            keywordResults = fullTextSearchService.search(query.query(), kwTopK);
            keywordAvailable = true;
            logger.debug("FTS search returned {} results", keywordResults.size());
        } catch (Exception e) {
            keywordFailure = e;
            logger.warn("FTS search failed: {}", e.getMessage());
        }

        // Both failed — throw
        if (!semanticAvailable && !keywordAvailable) {
            String msg = "Both semantic and keyword retrieval failed.";
            RetrievalException ex = new RetrievalException(msg);
            if (semanticFailure != null) ex.addSuppressed(semanticFailure);
            if (keywordFailure != null)  ex.addSuppressed(keywordFailure);
            throw ex;
        }

        // One or both succeeded — fuse
        List<RetrievedChunk> semList = semanticResults != null ? semanticResults : List.of();
        List<RetrievedChunk> kwList  = keywordResults  != null ? keywordResults  : List.of();

        List<RankedChunk> fused = rrfService.fuse(semList, kwList, cfg.getRrfK(), effectiveTopK);

        logger.info("Hybrid retrieval complete: semantic={}, keyword={}, fused={}",
                semList.size(), kwList.size(), fused.size());

        return new RetrievalResult(
                query.query(),
                fused,
                semList.size(),
                kwList.size(),
                fused.size(),
                semanticAvailable,
                keywordAvailable
        );
    }
}
