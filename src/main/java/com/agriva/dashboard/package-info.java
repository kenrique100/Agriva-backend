@org.springframework.modulith.ApplicationModule(
        displayName = "Dashboard",
        allowedDependencies = {
                "shared",
                "identity",
                "personnel",
                "payroll",
                "finance",
                "finance :: repository",
                "finance :: entity",
                "finance :: enums",
                "farming",
                "farming :: repository",
                "farming :: entity",
                "task",
                "task :: repository",
                "task :: entity"
        }
)
package com.agriva.dashboard;