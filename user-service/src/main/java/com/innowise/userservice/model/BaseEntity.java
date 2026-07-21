package com.innowise.userservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "createdAt", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BaseEntity that = (BaseEntity) obj;
        return this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        /* изза того что могут быть проблемы с тем что
        при использовании lazyload hibernate создает прокси классы
        и тот же this.getClass вернет класс прокси +
        если объект был transient и после становления persistent
        мог поменять свой id изза чего поменялся бы hash, в документации написано
        что лучше использовать Hibernate.getClass(this).hashCode()
        */
        return Hibernate.getClass(this).hashCode();
    }
}
