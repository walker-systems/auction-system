package com.walkersystems.sentinel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Rate Limiter", description = "Endpoint for checking Rate Limiter Service")
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    @Operation(
            summary = "Check Request Allowance",
            description = "Determines if a user request should be allowed based on Token Bucket algorithm. " +
                    "If allowed, consumes token from the bucket."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Request Allowed. Requested Tokens consumed.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RateLimitResponse.class)) // See DTO definition below
            ),

            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RateLimitResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error (check Redis connection).",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, Boolean>>> checkRateLimit(
            @Parameter(description = "Unique identifier for the user or service (UUID, API Key, IP)", example = "user_123")
            @RequestHeader(value = "X-User-ID", defaultValue = "guest") String userId,

            @Parameter(description = "Max number of tokens the bucket can hold", example = "10")
            @RequestParam(defaultValue = "10") int capacity,

            @Parameter(description = "Refill rate in tokens per second", example = "1")
            @RequestParam(defaultValue = "1") int rate,

            @Parameter(description = "Number of tokens this request costs", example = "1")
            @RequestParam(defaultValue = "1") int cost
    ) {
        return rateLimiterService.isAllowed(userId, capacity, rate, cost)
                .map(allowed -> {
                    if (allowed) {
                        return ResponseEntity.ok(Map.of("allowed", true));
                    } else {
                        return ResponseEntity.status(429).body(Map.of("allowed", false));
                    }
                });
    }

    // Simple DTO to describe JSON response for Open API documentation
    public record RateLimitResponse(
            @Schema(description = "Whether the request is permitted", example = "true")
            boolean allowed
    ) {}

}
