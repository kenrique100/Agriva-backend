package com.agriva.dashboard.service;

import com.agriva.dashboard.model.DashboardProductivityResponse;
import com.agriva.dashboard.model.DashboardResponse;

import java.io.PrintWriter;
import java.util.UUID;

public interface DashboardService {

    DashboardResponse personalDashboard(UUID userId, UUID farmId, String role);

    DashboardResponse farmDashboard(UUID farmId, UUID requesterUserId);

    DashboardProductivityResponse productivity(UUID userId, UUID farmId, String role);

    void exportFarmToCsv(UUID farmId, PrintWriter writer);
}