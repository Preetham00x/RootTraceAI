package com.roottrace.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private Diagnosis diagnosis = new Diagnosis();
    private Retrieval retrieval = new Retrieval();
    private Ingestion ingestion = new Ingestion();

    public Ingestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(Ingestion ingestion) {
        this.ingestion = ingestion;
    }

    public Diagnosis getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(Diagnosis diagnosis) {
        this.diagnosis = diagnosis;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public static class Diagnosis {
        private int maxContextChunks = 8;

        public int getMaxContextChunks() {
            return maxContextChunks;
        }

        public void setMaxContextChunks(int maxContextChunks) {
            this.maxContextChunks = maxContextChunks;
        }
    }

    public static class Retrieval {
        private int topK = 10;
        private int semanticTopK = 20;
        private int keywordTopK = 20;
        private int rrfK = 60;
        private double minScore = 0.0;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getSemanticTopK() {
            return semanticTopK;
        }

        public void setSemanticTopK(int semanticTopK) {
            this.semanticTopK = semanticTopK;
        }

        public int getKeywordTopK() {
            return keywordTopK;
        }

        public void setKeywordTopK(int keywordTopK) {
            this.keywordTopK = keywordTopK;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }
    }

    public static class Ingestion {
        private int chunkSize = 1200;
        private int chunkOverlap = 150;
        private long maxDocumentSize = 10485760;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public long getMaxDocumentSize() {
            return maxDocumentSize;
        }

        public void setMaxDocumentSize(long maxDocumentSize) {
            this.maxDocumentSize = maxDocumentSize;
        }
    }
}
