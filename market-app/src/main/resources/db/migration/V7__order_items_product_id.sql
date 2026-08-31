-- Снимок позиций заказа получает ссылку на исходный товар — это позволяет при завершении
-- checkout удалять из корзины только те позиции, что реально вошли в оплаченный заказ,
-- а не всю корзину пользователя целиком (CheckoutUseCase/OrderReconciliationService).
ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES product (id);
