@org.springframework.modulith.ApplicationModule(
        displayName = "Feeding",
        allowedDependencies = {
                "shared",
                "identity",
                "finance :: enums",
                "finance :: dto-request",
                "finance :: *"
        }
)
package com.agriva.feeding;