package com.agriva.dashboard.controller;

import com.agriva.dashboard.model.DashboardProductivityResponse;
import com.agriva.dashboard.model.DashboardResponse;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.dashboard.service.DashboardService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> myDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(dashboardService.personalDashboard(
                u.userId(), u.farmId(), u.role()));
    }

    @GetMapping("/farm")
    public ResponseEntity<DashboardResponse> farmDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        if (u == null) return ResponseEntity.status(401).build();
        if (!u.isMasterOrAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(dashboardService.farmDashboard(
                u.farmId(), u.userId()));
    }

    @GetMapping("/productivity")
    public ResponseEntity<DashboardProductivityResponse> productivity(
            @AuthenticationPrincipal AuthenticatedUser u) {
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(dashboardService.productivity(
                u.userId(), u.farmId(), u.role()));
    }

    @GetMapping("/farm/export")
    public void exportFarmData(
            @AuthenticationPrincipal AuthenticatedUser u,
            HttpServletResponse response) throws IOException {
        if (u == null || !u.isMasterOrAdmin()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"farm_dashboard.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            dashboardService.exportFarmToCsv(u.farmId(), writer);
        }
    }
}