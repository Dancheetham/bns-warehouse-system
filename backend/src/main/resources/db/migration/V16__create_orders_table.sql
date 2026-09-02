CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    order_date TIMESTAMP NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    order_reference VARCHAR(255),
    ecommerce_order_number VARCHAR(255),
    ordered_by VARCHAR(255),
    delivery_name VARCHAR(255),
    delivery_town VARCHAR(255),
    delivery_country VARCHAR(255),
    delivery_postcode VARCHAR(50),
    delivery_country_code VARCHAR(10),
    status VARCHAR(50) NOT NULL DEFAULT 'ON_HOLD',
    order_type VARCHAR(50) NOT NULL DEFAULT 'ORDER',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_orders_order_date ON orders (order_date);
CREATE INDEX idx_orders_status ON orders (status);
