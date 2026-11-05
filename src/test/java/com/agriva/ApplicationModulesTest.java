package com.agriva;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules.of(AgrivaApplication.class)
                .verify();
    }
}