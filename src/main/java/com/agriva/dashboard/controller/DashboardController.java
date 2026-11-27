package com.agriva.dashboard.controller;

import com.agriva.dashboard.model.DashboardProductivityResponse;
import com.agriva.dashboard.model.DashboardResponse;
import com.agriva.dashboard.service.DashboardService;
import com.agriva.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardResponse> myDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        requirePrincipal(u);
        return ResponseEntity.ok(dashboardService.personalDashboard(
                u.userId(), u.farmId(), u.role()));
    }

    @GetMapping("/farm")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<DashboardResponse> farmDashboard(
            @AuthenticationPrincipal AuthenticatedUser u) {
        requirePrincipal(u);
        return ResponseEntity.ok(dashboardService.farmDashboard(
                u.farmId(), u.userId()));
    }

    @GetMapping("/productivity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardProductivityResponse> productivity(
            @AuthenticationPrincipal AuthenticatedUser u) {
        requirePrincipal(u);
        return ResponseEntity.ok(dashboardService.productivity(
                u.userId(), u.farmId(), u.role()));
    }

    @GetMapping("/farm/export")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public void exportFarmData(
            @AuthenticationPrincipal AuthenticatedUser u,
            HttpServletResponse response) throws IOException {
        requirePrincipal(u);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"farm_dashboard.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            dashboardService.exportFarmToCsv(u.farmId(), writer);
        }
    }

    private static void requirePrincipal(AuthenticatedUser u) {
        if (u == null) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
    }
}