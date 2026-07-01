CREATE TABLE ticket_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    expected_price DECIMAL(12,2),
    actual_price DECIMAL(12,2),
    payment_mode VARCHAR(20),
    payment_datetime DATETIME,
    status VARCHAR(50),
    CONSTRAINT fk_tp_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);
