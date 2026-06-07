package com.laminar.category.presentation;

import com.laminar.category.application.CardCategoryService;
import com.laminar.category.domain.CardCategoryEntity;
import com.laminar.category.presentation.CardCategoryDtos.CategoryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/categories — 현재 주제의 카드 카테고리 CRUD. */
@RestController
@RequestMapping("/api/categories")
public class CardCategoryController {

  private final CardCategoryService service;

  public CardCategoryController(CardCategoryService service) {
    this.service = service;
  }

  @GetMapping
  public List<CategoryResponse> list() {
    return service.list().stream().map(CardCategoryController::toResponse).toList();
  }

  @PostMapping
  public CategoryResponse create(@Valid @RequestBody CardCategoryDtos.CreateRequest request) {
    return toResponse(service.create(request.name(), request.color()));
  }

  @PatchMapping("/{id}")
  public CategoryResponse update(
      @PathVariable UUID id, @Valid @RequestBody CardCategoryDtos.UpdateRequest request) {
    return toResponse(service.update(id, request.name(), request.color()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  static CategoryResponse toResponse(CardCategoryEntity c) {
    return new CategoryResponse(c.getId(), c.getName(), c.getColor());
  }
}
