package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A security role. Stored as a table rather than an enum column so that an
 * administrator can see and manage the role catalogue through the admin module.
 *
 * <p>Role names are persisted without the {@code ROLE_} prefix; the prefix is
 * added when building Spring Security authorities.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    /** CUSTOMER, BANK_STAFF, ADMIN or FRAUD_ANALYST. */
    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(length = 160)
    private String description;

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
