package com.example.be_a.user.application;

import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.domain.UserRole;
import org.springframework.stereotype.Service;

@Service
public class UserAuthorizationService {

    public void requireCreator(CurrentUserInfo user) {
        if (user.role() != UserRole.CREATOR) {
            throw new ApiException(ErrorCode.CREATOR_ONLY);
        }
    }

    public void requireOwner(CurrentUserInfo user, Long ownerId) {
        if (!user.id().equals(ownerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
