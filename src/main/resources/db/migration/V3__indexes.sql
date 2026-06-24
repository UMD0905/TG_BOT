-- Booking slot conflict queries: find overlapping bookings efficiently
CREATE INDEX idx_booking_schedule ON booking (scheduled_at, end_at, status);

-- Load all bookings for a specific client
CREATE INDEX idx_booking_client ON booking (client_id, status);

-- Bot state lookup by Telegram user
-- (telegram_user_id is already PK, index implicit)

-- Car models by brand (used when building brand→model dropdown)
CREATE INDEX idx_car_model_brand ON car_model (brand_id);

-- Cars by client (used to build "pick your car" keyboard)
CREATE INDEX idx_car_client ON car (client_id);

-- Services by category (used when building category→service dropdown)
CREATE INDEX idx_service_category ON service (category_id, active);
