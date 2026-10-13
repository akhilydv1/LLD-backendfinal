package com.designloop.controller;

import com.designloop.domain.ProblemDocument;
import com.designloop.exception.ApiException;
import com.designloop.repository.ProblemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
@Tag(name = "Problem library", description = "Seeded Low-Level Design exercises and rubric metadata")
public class ProblemController {
    private final ProblemRepository problems;
    public ProblemController(ProblemRepository problems) { this.problems = problems; }

    @GetMapping
    @Operation(summary = "List available LLD problems")
    public List<ProblemDocument> list() { return problems.findAll().stream().sorted(java.util.Comparator.comparing(ProblemDocument::getId)).toList(); }

    @GetMapping("/{id}")
    @Operation(summary = "Get problem details, requirements, assumptions and rubric")
    public ProblemDocument get(@PathVariable String id) {
        return problems.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "No problem exists with id '" + id + "'."));
    }
}
