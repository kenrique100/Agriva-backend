CREATE TABLE salary_payments (
                                 id                 UUID            PRIMARY KEY,
                                 farm_id            UUID            NOT NULL,
                                 personnel_id       UUID            NOT NULL,
                                 amount             NUMERIC(19, 4)  NOT NULL,
                                 payment_date       DATE            NOT NULL,
                                 status             VARCHAR(20)     NOT NULL DEFAULT 'PROCESSED',
                                 payment_reference  VARCHAR(50)     NOT NULL,
                                 expense_id         UUID,
                                 paid_by_user_id    UUID,
                                 created_at         TIMESTAMP       NOT NULL DEFAULT NOW(),

                                 CONSTRAINT fk_sp_farm
                                     FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_personnel
                                     FOREIGN KEY (personnel_id) REFERENCES personnel(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_expense
                                     FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE SET NULL,
                                 CONSTRAINT fk_sp_paid_by
                                     FOREIGN KEY (paid_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
                                 CONSTRAINT ux_salary_payments_ref UNIQUE (payment_reference),
                                 CONSTRAINT chk_sp_status
                                     CHECK (status IN ('PENDING','PROCESSED','FAILED','CANCELLED')),
                                 CONSTRAINT chk_sp_amount_positive
                                     CHECK (amount > 0)
);

CREATE INDEX idx_sp_farm       ON salary_payments (farm_id);
CREATE INDEX idx_sp_personnel  ON salary_payments (personnel_id);
CREATE INDEX idx_sp_date       ON salary_payments (payment_date);
CREATE INDEX idx_sp_expense    ON salary_payments (expense_id);