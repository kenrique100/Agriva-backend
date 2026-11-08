package com.agriva.shared.port;

import java.util.UUID;

public interface FileOwnershipPort {
    boolean isOwnedByFarm(String fileName, UUID farmId);
}