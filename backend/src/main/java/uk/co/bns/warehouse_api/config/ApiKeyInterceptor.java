package uk.co.bns.warehouse_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.co.bns.warehouse_api.dto.ErrorResponse;
import uk.co.bns.warehouse_api.service.ApiKeyService;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader("X-API-Key");

        if (!apiKeyService.validate(key)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            ErrorResponse body = new ErrorResponse(
                    LocalDateTime.now(), 401, "Unauthorized",
                    "Missing or invalid API key. Include a valid key in the X-API-Key header."
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }
}
