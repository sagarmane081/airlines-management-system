package com.services.dto;

import com.services.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    private String fullName;
    private String email;
    private String password;
    private Role role;
}