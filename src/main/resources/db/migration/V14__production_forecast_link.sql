-- Links a physical production batch to the forecast that requested it
ALTER TABLE feed_batch ADD COLUMN forecast_id UUID;