package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;

public record PickUndoRequest(@NotNull Long stockItemId) {}
