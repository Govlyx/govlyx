package com.JanSahayak.AI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Table(name = "post_translations", indexes = {
    @Index(name = "idx_post_trans_ref_lang", columnList = "reference_id, reference_type, target_language", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_type", nullable = false, length = 20)
    private String referenceType; // "POST" or "SOCIAL_POST"

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage; // e.g., "hi", "mr"

    @Column(name = "translated_text", columnDefinition = "TEXT", nullable = false)
    private String translatedText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Date createdAt;
}
