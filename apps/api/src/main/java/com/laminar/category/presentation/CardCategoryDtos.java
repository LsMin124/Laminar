package com.laminar.category.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CardCategoryDtos {

  private CardCategoryDtos() {}

  public record CreateRequest(
      @NotBlank @Size(max = 100) String name, @Size(max = 32) String color) {}

  public record UpdateRequest(@Size(max = 100) String name, @Size(max = 32) String color) {}

  public record CategoryResponse(UUID id, String name, String color) {}
}
