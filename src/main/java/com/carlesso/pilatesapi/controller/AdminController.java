package com.carlesso.pilatesapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin", description = "Endpoints administrativos protegidos por role ADMIN")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Operation(summary = "Health administrativo", description = "Requer role ADMIN. Endpoint inicial administrativo.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
