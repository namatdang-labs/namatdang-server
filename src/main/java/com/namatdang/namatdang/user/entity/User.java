package com.namatdang.namatdang.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_accounts")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public User(String email, String password, String name, String phoneNumber) {
        this.email = email;
        this.password = password;
        this.name = name.strip();
        this.phoneNumber = phoneNumber.strip();
        this.roles.add(UserRole.CONSUMER);
    }

    public User(String email, String password, String name, String phoneNumber, UserRole role) {
        this(email, password, name, phoneNumber);
        if (role == UserRole.OWNER) {
            grantOwnerRole();
        }
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(String name, String phoneNumber) {
        if (name != null) {
            this.name = name.strip();
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber.strip();
        }
    }

    public Set<UserRole> getRoles() {
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public void grantOwnerRole() {
        roles.add(UserRole.CONSUMER);
        roles.add(UserRole.OWNER);
    }
}
