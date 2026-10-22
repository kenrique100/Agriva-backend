package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.dashboard.DashboardResponse;
import com.agriva.service.AuthService;
import com.agriva.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> myDashboard(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(dashboardService.personalDashboard(
                token.getUserId(), token.getFarmId(), token.getRole()));
    }

    @GetMapping("/farm")
    public ResponseEntity<DashboardResponse> farmDashboard(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        if (!"MASTER".equals(token.getRole())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(dashboardService.farmDashboard(
                token.getFarmId(), token.getUserId()));
    }

    @GetMapping("/productivity")
    public ResponseEntity<com.agriva.model.dto.dashboard.DashboardProductivityResponse> productivity(
            @RequestHeader("Authorization") String authHeader) {
        var token = authService.validateToken(authHeader);
        return ResponseEntity.ok(dashboardService.productivity(
                token.getUserId(), token.getFarmId(), token.getRole()));
    }
}