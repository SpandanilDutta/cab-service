package com.cabservice.user.entity;

import com.cabservice.common.util.AppConstant;
import com.cabservice.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * One row per real person. Being a rider, driver or admin is expressed through
 * the roles set (stored in the user_roles table) plus the profile tables,
 * never through duplicate user rows.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "mobile" , nullable = false, unique = true, length = 20)
    private String mobile;

    @Column(name = "email" , unique = true, length = 255)
    private String email;

    @Column(name = "username", unique = true, length = 255)
    private String username;

    // bcrypt/argon2 hash, never the raw password
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Hibernate creates user_roles(user_id, role) with a composite primary key
    // on both columns, so the same role cannot be assigned to a person twice.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<AppConstant.Role> roles = new HashSet<>();

}