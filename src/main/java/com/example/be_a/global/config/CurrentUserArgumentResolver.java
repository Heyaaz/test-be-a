package com.example.be_a.global.config;

import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.domain.UserRepository;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserRepository userRepository;

    public CurrentUserArgumentResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && CurrentUserInfo.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String rawUserId = webRequest.getHeader(USER_ID_HEADER);
        if (!StringUtils.hasText(rawUserId)) {
            throw new ApiException(ErrorCode.MISSING_USER_ID);
        }

        Long userId;
        try {
            userId = Long.parseLong(rawUserId.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_USER_ID);
        }

        return userRepository.findById(userId)
            .map(user -> new CurrentUserInfo(user.getId(), user.getRole()))
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
