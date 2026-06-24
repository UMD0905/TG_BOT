-- ─── Service Categories ───────────────────────────────────────────────────────
INSERT INTO service_category (name, description, sort_order) VALUES
('Техническое обслуживание', 'Плановое ТО: замена масла, фильтров, жидкостей', 1),
('Диагностика',             'Компьютерная и ручная диагностика неисправностей', 2),
('Ремонт',                  'Ремонт ходовой, тормозов, двигателя и трансмиссии', 3),
('Шиномонтаж',              'Монтаж, демонтаж, балансировка и ремонт шин', 4),
('Кузовные работы',         'Полировка, покраска, удаление вмятин', 5);

-- ─── Services ────────────────────────────────────────────────────────────────
-- Maintenance (category 1)
INSERT INTO service (category_id, name, description, duration_minutes, price_from, price_to) VALUES
(1, 'Замена масла и фильтра',      'Замена моторного масла и масляного фильтра',                    45,  150000,  300000),
(1, 'Замена воздушного фильтра',   'Замена фильтра очистки воздуха',                               20,   50000,  100000),
(1, 'Замена свечей зажигания',     'Замена комплекта свечей зажигания',                            30,  100000,  250000),
(1, 'Замена тормозной жидкости',   'Прокачка и замена тормозной жидкости',                        30,   80000,  150000),
(1, 'Замена антифриза',            'Слив, промывка и заправка новым антифризом',                   30,  100000,  180000);

-- Diagnostics (category 2)
INSERT INTO service (category_id, name, description, duration_minutes, price_from, price_to) VALUES
(2, 'Компьютерная диагностика',    'Подключение к ЭБУ, считывание ошибок, анализ',                60,  100000,  200000),
(2, 'Диагностика подвески',        'Осмотр и диагностика элементов ходовой части',                45,   80000,  150000),
(2, 'Диагностика тормозной системы','Проверка колодок, дисков, цилиндров и жидкости',             30,   60000,  100000);

-- Repair (category 3)
INSERT INTO service (category_id, name, description, duration_minutes, price_from, price_to) VALUES
(3, 'Замена тормозных колодок',    'Замена передних или задних тормозных колодок',                 60,  200000,  400000),
(3, 'Замена амортизаторов',        'Замена одной пары амортизаторов (перед или зад)',             90,  400000,  800000),
(3, 'Ремонт рулевой рейки',        'Диагностика и ремонт рулевой рейки',                         120,  500000, 1000000),
(3, 'Замена ремня ГРМ',            'Замена ремня и роликов газораспределительного механизма',    120,  400000,  800000),
(3, 'Замена сцепления',            'Замена диска, корзины и выжимного подшипника сцепления',     180,  600000, 1200000);

-- Tire Service (category 4)
INSERT INTO service (category_id, name, description, duration_minutes, price_from, price_to) VALUES
(4, 'Шиномонтаж (1 колесо)',       'Снятие, монтаж и установка одного колеса',                   15,   30000,   50000),
(4, 'Балансировка (1 колесо)',     'Балансировка одного колеса на стенде',                       15,   30000,   50000),
(4, 'Сезонная смена шин',          'Перестановка 4 колёс с балансировкой',                       60,  200000,  350000),
(4, 'Ремонт прокола',              'Ремонт прокола камеры или бескамерной шины',                 20,   30000,   60000);

-- Bodywork (category 5)
INSERT INTO service (category_id, name, description, duration_minutes, price_from, price_to) VALUES
(5, 'Полировка кузова',            'Машинная полировка всего кузова',                            180,  500000, 1000000),
(5, 'Покраска детали',             'Подготовка и окраска одного кузовного элемента',             240,  800000, 2000000),
(5, 'Удаление вмятин',             'Беспокрасочное удаление вмятин (PDR)',                       60,  200000,  500000);

-- ─── Car Brands ──────────────────────────────────────────────────────────────
INSERT INTO car_brand (name) VALUES
('Toyota'),
('Hyundai'),
('Chevrolet'),
('Lada'),
('Kia'),
('Nissan'),
('BMW'),
('Mercedes-Benz'),
('Volkswagen'),
('Ravon'),
('Daewoo'),
('Honda'),
('Mitsubishi'),
('Mazda'),
('Renault');

-- ─── Car Models ──────────────────────────────────────────────────────────────
-- Toyota (brand_id = 1)
INSERT INTO car_model (brand_id, name) VALUES
(1, 'Camry'), (1, 'Corolla'), (1, 'RAV4'), (1, 'Land Cruiser'), (1, 'Prius');

-- Hyundai (brand_id = 2)
INSERT INTO car_model (brand_id, name) VALUES
(2, 'Sonata'), (2, 'Elantra'), (2, 'Tucson'), (2, 'Santa Fe'), (2, 'Accent');

-- Chevrolet (brand_id = 3)
INSERT INTO car_model (brand_id, name) VALUES
(3, 'Malibu'), (3, 'Cruze'), (3, 'Captiva'), (3, 'Spark'), (3, 'Lacetti');

-- Lada (brand_id = 4)
INSERT INTO car_model (brand_id, name) VALUES
(4, 'Priora'), (4, 'Granta'), (4, 'Vesta'), (4, 'Niva'), (4, 'Kalina');

-- Kia (brand_id = 5)
INSERT INTO car_model (brand_id, name) VALUES
(5, 'Optima'), (5, 'Rio'), (5, 'Sportage'), (5, 'Sorento'), (5, 'Cerato');

-- Nissan (brand_id = 6)
INSERT INTO car_model (brand_id, name) VALUES
(6, 'Sentra'), (6, 'Tiida'), (6, 'X-Trail'), (6, 'Patrol'), (6, 'Almera');

-- BMW (brand_id = 7)
INSERT INTO car_model (brand_id, name) VALUES
(7, '3 Series'), (7, '5 Series'), (7, '7 Series'), (7, 'X3'), (7, 'X5');

-- Mercedes-Benz (brand_id = 8)
INSERT INTO car_model (brand_id, name) VALUES
(8, 'C-Class'), (8, 'E-Class'), (8, 'S-Class'), (8, 'GLC'), (8, 'GLE');

-- Volkswagen (brand_id = 9)
INSERT INTO car_model (brand_id, name) VALUES
(9, 'Polo'), (9, 'Passat'), (9, 'Golf'), (9, 'Tiguan'), (9, 'Jetta');

-- Ravon (brand_id = 10)
INSERT INTO car_model (brand_id, name) VALUES
(10, 'R2'), (10, 'R3'), (10, 'R4'), (10, 'Nexia R3'), (10, 'Gentra');

-- Daewoo (brand_id = 11)
INSERT INTO car_model (brand_id, name) VALUES
(11, 'Nexia'), (11, 'Matiz'), (11, 'Epica'), (11, 'Nubira'), (11, 'Lanos');

-- Honda (brand_id = 12)
INSERT INTO car_model (brand_id, name) VALUES
(12, 'Accord'), (12, 'Civic'), (12, 'CR-V'), (12, 'HR-V'), (12, 'Fit');

-- Mitsubishi (brand_id = 13)
INSERT INTO car_model (brand_id, name) VALUES
(13, 'Lancer'), (13, 'Outlander'), (13, 'Pajero'), (13, 'ASX'), (13, 'Eclipse Cross');

-- Mazda (brand_id = 14)
INSERT INTO car_model (brand_id, name) VALUES
(14, 'Mazda3'), (14, 'Mazda6'), (14, 'CX-5'), (14, 'CX-9'), (14, 'MX-5');

-- Renault (brand_id = 15)
INSERT INTO car_model (brand_id, name) VALUES
(15, 'Logan'), (15, 'Sandero'), (15, 'Duster'), (15, 'Megane'), (15, 'Fluence');

-- ─── Working Hours ───────────────────────────────────────────────────────────
-- Mon–Sat 09:00–18:00 open; Sun closed
INSERT INTO working_hours (day_of_week, open_time, close_time, is_working) VALUES
(1, '09:00', '18:00', TRUE),   -- Monday
(2, '09:00', '18:00', TRUE),   -- Tuesday
(3, '09:00', '18:00', TRUE),   -- Wednesday
(4, '09:00', '18:00', TRUE),   -- Thursday
(5, '09:00', '18:00', TRUE),   -- Friday
(6, '09:00', '16:00', TRUE),   -- Saturday (shorter day)
(7, '09:00', '18:00', FALSE);  -- Sunday (closed)
