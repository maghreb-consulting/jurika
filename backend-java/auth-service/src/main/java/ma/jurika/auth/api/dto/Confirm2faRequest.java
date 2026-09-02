package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record Confirm2faRequest(@Min(0) @Max(999999) int code) {}
