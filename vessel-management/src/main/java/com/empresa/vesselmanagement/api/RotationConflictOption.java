package com.empresa.vesselmanagement.api;

/** FR-014: as duas saídas oferecidas ao proprietário quando rodízio e Alto Mar colidem. */
public record RotationConflictOption(String action, String description) {
}
