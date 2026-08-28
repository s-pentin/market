package org.market.app.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.market.app.models.Product;

import java.util.List;

/**
 * Сырой результат поиска товаров (список + общее число) — без DTO и без счётчиков
 * корзины, чтобы его можно было безопасно кешировать независимо от пользователя.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResult {
    private List<Product> products;
    private long total;
}
