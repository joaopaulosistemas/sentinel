package com.sentinel.audit.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Sugestão do Claude sobre um sistema ("Revisão do Claude"). Fica numa tabela própria: nunca vira achado
 * e nunca entra na nota.
 */
@Entity
@Table(name = "review_suggestion", indexes = @Index(name = "idx_review_suggestion_system", columnList = "system_id"))
public class ReviewSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "review_suggestion_seq")
    @SequenceGenerator(name = "review_suggestion_seq", sequenceName = "review_suggestion_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String detail;

    @Column(name = "rule_ref", length = 255)
    private String ruleRef;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("PMD.ImmutableField") // o Hibernate atribui o campo ao carregar a entidade: não pode ser final
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getRuleRef() { return ruleRef; }
    public void setRuleRef(String ruleRef) { this.ruleRef = ruleRef; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public SuggestionStatus getStatus() { return status; }
    public void setStatus(SuggestionStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
