-- Users
CREATE INDEX idx_users_farm_id ON users (farm_id);
CREATE INDEX idx_users_role    ON users (role);

-- Farms
CREATE INDEX idx_farms_master_id ON farms (master_id);

-- Registration dupe checks
CREATE INDEX idx_users_email_lower    ON users (LOWER(email));
CREATE INDEX idx_users_username_lower ON users (LOWER(username));