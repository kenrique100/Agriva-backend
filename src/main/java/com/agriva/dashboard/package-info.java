@org.springframework.modulith.ApplicationModule(
        displayName = "Dashboard",
        allowedDependencies = {"shared", "finance", "farming", "personnel", "task", "payroll"}
)
package com.agriva.dashboard;