package com.empresa.vesselmanagement.api;

import java.util.List;

public record RotationConflictResponse(String error, String message, List<RotationConflictOption> options) {
}
