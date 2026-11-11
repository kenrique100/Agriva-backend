

ALTER TABLE formulation_line
DROP CONSTRAINT fk_formulation_line_ingredient,
    ADD  CONSTRAINT fk_formulation_line_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredient (id) ON DELETE CASCADE;

ALTER TABLE feed_batch
DROP CONSTRAINT fk_feed_batch_formulation,
    ADD  CONSTRAINT fk_feed_batch_formulation
        FOREIGN KEY (formulation_id) REFERENCES formulation (id) ON DELETE CASCADE;