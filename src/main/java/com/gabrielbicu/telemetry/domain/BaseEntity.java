package com.gabrielbicu.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Objects;

/**
 * Common fields shared by every entity: a generated id and a creation timestamp.
 *
 * <p>{@code @MappedSuperclass} means this class is not mapped to its own table —
 * its columns are inherited by each concrete entity's table.
 *
 * <p>{@code equals/hashCode} are based on the id, which is the recommended JPA
 * practice. If a class used the default identity-based {@code hashCode}, the hash
 * would change once Hibernate assigns an id after persist, silently breaking any
 * {@link java.util.Set} or {@link java.util.Map} the entity was stored in.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    public Long getId()   { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Return a fixed value for transient (not-yet-persisted) entities so the
        // hash stays stable across the persist boundary. Once an id is assigned,
        // equals() already uses it, so this is a pragmatic, JPA-safe choice.
        return id == null ? 0 : id.hashCode();
    }
}
