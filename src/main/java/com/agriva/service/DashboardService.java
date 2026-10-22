package com.agriva.service;

import com.agriva.model.dto.dashboard.DashboardProductivityResponse;
import com.agriva.model.dto.dashboard.DashboardResponse;

import java.util.UUID;

public interface DashboardService {

    DashboardResponse personalDashboard(UUID userId, UUID farmId, String role);

    DashboardResponse farmDashboard(UUID farmId, UUID requesterUserId);

    DashboardProductivityResponse productivity(UUID userId, UUID farmId, String role);
}