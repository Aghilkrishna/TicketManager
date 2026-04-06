package com.example.ticketmanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_id_proofs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "id_proof_type", nullable = false, length = 50)
    private String idProofType;

    @Column(name = "id_proof_document", columnDefinition = "BYTEA")
    private byte[] idProofDocument;

    @Column(name = "id_proof_content_type", length = 100)
    private String idProofContentType;

    @Column(name = "id_proof_file_name", length = 200)
    private String idProofFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "upload_status", length = 20)
    @Builder.Default
    private String uploadStatus = "UPLOADED";

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "verification_notes", length = 500)
    private String verificationNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
