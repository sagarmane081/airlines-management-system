package com.services.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(unique = true)
    private String email;

    // Optional - Twilio requires E.164 format (e.g. +15551234567) to actually send an SMS, but
    // this is not validated/normalized here. A booking's confirmation SMS is silently skipped for
    // any user without one, same graceful-skip pattern as a missing email.
    private String phoneNumber;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;
}