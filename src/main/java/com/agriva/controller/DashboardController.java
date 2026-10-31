package com.agriva.controller;

import com.agriva.model.dto.dashboard.DashboardProductivityResponse;
import com.agriva.model.dto.dashboard.DashboardResponse;
import com.agriva.security.AuthenticatedUser;
import com.agriva.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> myDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(dashboardService.personalDashboard(
                u.userId(), u.farmId(), u.role()));
    }

    @GetMapping("/farm")
    public ResponseEntity<DashboardResponse> farmDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        if (!u.isMasterOrAdmin()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(dashboardService.farmDashboard(
                u.farmId(), u.userId()));
    }

    @GetMapping("/productivity")
    public ResponseEntity<DashboardProductivityResponse> productivity(
            @AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(dashboardService.productivity(
                u.userId(), u.farmId(), u.role()));
    }
}