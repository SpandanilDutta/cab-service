package com.cabservice.common.entity;

import com.cabservice.common.util.IpUtil;
import com.cabservice.security.config.UserContextHolder;
import com.cabservice.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor  // ✅ Required for JPA
@AllArgsConstructor // ✅ Required for @SuperBuilder
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    @JsonIgnore
    private UUID createdBy;

    @Column(name = "created_by_ip", length = 45, updatable = false)
    @JsonIgnore
    private String createdByIp;

    @Column(name = "updated_by", length = 100)
    @JsonIgnore
    private UUID updatedBy;

    @Column(name = "updated_by_ip", length = 45)
    @JsonIgnore
    private String updatedByIp;

    @JoinColumn(name = "deleted_by")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private User deletedBy;

    @PrePersist
    public void prePersistAudit() {
        UUID currentUserId = UserContextHolder.getCurrentUserId();
        if (this.createdBy == null && currentUserId != null) {
            this.createdBy = currentUserId;
        }
        if (this.updatedBy == null && currentUserId != null) {
            this.updatedBy = currentUserId;
        }
        String currentIp = resolveClientIp();
        if (this.createdByIp == null && currentIp != null) {
            this.createdByIp = currentIp;
        }
        if (this.updatedByIp == null && currentIp != null) {
            this.updatedByIp = currentIp;
        }
    }

    private static String resolveClientIp() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return IpUtil.getClientIp(servletAttributes.getRequest());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
