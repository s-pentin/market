-- Старые данные без владельца очищаем перед добавлением NOT NULL-связи на users:
-- до этого спринта корзина/заказы были глобальными (один покупатель на всё приложение).
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_item;

-- Корзина: составная уникальность (user_id, product_id) вместо глобальной UNIQUE(product_id),
-- чтобы два разных покупателя могли одновременно держать один и тот же товар в своих корзинах.
ALTER TABLE cart_item DROP CONSTRAINT IF EXISTS cart_item_product_id_key;
ALTER TABLE cart_item ADD COLUMN user_id BIGINT NOT NULL REFERENCES users (id);
ALTER TABLE cart_item ADD CONSTRAINT uq_cart_item_user_product UNIQUE (user_id, product_id);

-- Заказы: привязка к пользователю.
ALTER TABLE orders ADD COLUMN user_id BIGINT NOT NULL REFERENCES users (id);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders (user_id);

-- Баланс: переход на per-user модель вместо единственной строки id=1.
-- Новые строки создаются лениво в PaymentService.getBalance() при первом обращении пользователя.
ALTER TABLE balance ADD COLUMN user_id BIGINT UNIQUE REFERENCES users (id);
DELETE FROM balance;
ALTER TABLE balance ALTER COLUMN user_id SET NOT NULL;
