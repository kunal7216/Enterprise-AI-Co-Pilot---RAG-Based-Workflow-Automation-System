package com.enterprise.copilot.repository;

import com.enterprise.copilot.entity.User;
import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.enums.DecisionType;
import com.enterprise.copilot.enums.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    // ─── Paginated listings ───────────────────────────────────────────────────
    Page<Workflow> findByCreatedBy(User user, Pageable pageable);

    Page<Workflow> findByStatus(WorkflowStatus status, Pageable pageable);

    Page<Workflow> findByCreatedByAndStatus(User user, WorkflowStatus status, Pageable pageable);

    // ─── Counts ───────────────────────────────────────────────────────────────
    long countByCreatedBy(User user);

    long countByStatus(WorkflowStatus status);

    long countByCreatedByAndStatus(User user, WorkflowStatus status);

    long countByDecisionType(DecisionType decisionType);

    // ─── Aggregate KPIs (used by DashboardStats) ──────────────────────────────
    @Query("SELECT AVG(w.confidenceScore) FROM Workflow w WHERE w.confidenceScore IS NOT NULL")
    Optional<Double> findAverageConfidenceScore();

    @Query("SELECT AVG(w.processingTimeMs) FROM Workflow w WHERE w.processingTimeMs IS NOT NULL")
    Optional<Double> findAverageProcessingTimeMs();

    // ─── Anomaly detection — past invoices for same vendor ────────────────────
    @Query("SELECT w FROM Workflow w WHERE LOWER(w.extractedData) LIKE LOWER(CONCAT('%', :vendor, '%'))")
    List<Workflow> findByVendorInExtractedData(@Param("vendor") String vendor);

    // ─── RAG / pgvector: semantic similarity search ───────────────────────────
    @Query(value = """
        SELECT * FROM workflows
        WHERE embedding IS NOT NULL
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 3
        """, nativeQuery = true)
    List<Workflow> findSimilar(@Param("embedding") String embedding);

    // ─── RAG / pgvector: semantic similarity search with dynamic limit ────────
    @Query(value = """
        SELECT * FROM workflows
        WHERE embedding IS NOT NULL
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Workflow> findSimilarWithLimit(@Param("embedding") String embedding,
                                        @Param("limit") int limit);

    // ─── RAG / pgvector: vendor + semantic similarity hybrid search ───────────
    @Query(value = """
        SELECT * FROM workflows
        WHERE embedding IS NOT NULL
          AND extracted_data IS NOT NULL
          AND LOWER(extracted_data) LIKE LOWER(CONCAT('%', :vendor, '%'))
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 3
        """, nativeQuery = true)
    List<Workflow> findSimilarByVendor(@Param("vendor") String vendor,
                                       @Param("embedding") String embedding);

    // ─── Update embedding after extraction / processing ───────────────────────
    @Modifying
    @Query(value = """
        UPDATE workflows
        SET embedding = CAST(:embedding AS vector)
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);
    boolean existsByDocumentName(String documentName);

    @Query(value = """
            SELECT * FROM workflows
            WHERE embedding IS NOT NULL
            AND 1 - (embedding <=> CAST(:vector AS vector)) >= :threshold
            ORDER BY embedding <=> CAST(:vector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Workflow> findSimilarWithThreshold(
            @Param("vector") String vector,
            @Param("limit") int limit,
            @Param("threshold") double threshold);
}