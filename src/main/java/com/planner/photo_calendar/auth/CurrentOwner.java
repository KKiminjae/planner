package com.planner.photo_calendar.auth;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentOwner {
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof OwnerPrincipal owner) {
            return owner.ownerId();
        }
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
