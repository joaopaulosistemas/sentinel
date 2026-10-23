package com.sentinel.audit.review;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Regra proposta para um sistema e o ponto em que está no funil de promoção. */
@Entity
@Table(name = "rule_proposal",
        uniqueConstraints = @UniqueConstraint(name = "uk_rule_proposal_slug", columnNames = {"system_id", "slug"}))
public class RuleProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_proposal_seq")
    @SequenceGenerator(name = "rule_proposal_seq", sequenceName = "rule_proposal_seq", allocationSize = 1)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String rationale;

    @Column(name = "semgrep_yaml", nullable = false, length = 8000)
    private String semgrepYaml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStage stage = RuleStage.PROPOSED;

    @Column(name = "copied_from_id")
    private Long copiedFromId;

    @Column(name = "sample_note", length = 500)
    private String sampleNote;

    @Column(name = "observation_started_at")
    private LocalDateTime observationStartedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getSemgrepYaml() { return semgrepYaml; }
    public void setSemgrepYaml(String semgrepYaml) { this.semgrepYaml = semgrepYaml; }
    public RuleStage getStage() { return stage; }
    public void setStage(RuleStage stage) { this.stage = stage; }
    public Long getCopiedFromId() { return copiedFromId; }
    public void setCopiedFromId(Long copiedFromId) { this.copiedFromId = copiedFromId; }
    public String getSampleNote() { return sampleNote; }
    public void setSampleNote(String sampleNote) { this.sampleNote = sampleNote; }
    public LocalDateTime getObservationStartedAt() { return observationStartedAt; }
    public void setObservationStartedAt(LocalDateTime t) { this.observationStartedAt = t; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
