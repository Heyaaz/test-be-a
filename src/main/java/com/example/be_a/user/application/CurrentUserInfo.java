package com.example.be_a.user.application;

import com.example.be_a.user.domain.UserRole;

public record CurrentUserInfo(
    Long id,
    UserRole role
) {
}
