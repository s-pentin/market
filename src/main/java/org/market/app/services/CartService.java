package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.exceptions.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductsInCart getAllProductsInCart() {
        List<CartItem> cartItems = cartItemRepository.findAll();

        BigDecimal totalCost = BigDecimal.ZERO;
        List<ItemDto> items = new ArrayList<>();
        for (CartItem cartItem: cartItems) {
            ItemDto item = toItem(cartItem);
            items.add(item);
            totalCost = totalCost.add(item.getPrice().multiply(BigDecimal.valueOf(item.getCount())));
        }

        return ProductsInCart.builder()
                .items(items)
                .totalCost(totalCost)
                .build();
    }

    @Transactional
    public void changeCount(Long productId, Action action) {
        Optional<CartItem> cartOpt = cartItemRepository.findByProductId(productId);
        switch (action) {
            case PLUS -> plus(productId, cartOpt);
            case MINUS -> minus(cartOpt);
            case DELETE -> delete(cartOpt);
        }
    }

    private void plus(Long productId, Optional<CartItem> cartOpt) {
        if (cartOpt.isPresent()) {
            CartItem cart = cartOpt.get();
            cart.setCount(cart.getCount() + 1);
            cartItemRepository.save(cart);
        } else {
            Product product = productRepository
                    .findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

            CartItem cart = new CartItem();
            cart.setProduct(product);
            cart.setCount(1);

            cartItemRepository.save(cart);
        }
    }

    private void minus(Optional<CartItem> cartOpt) {
        if (cartOpt.isPresent()) {
            CartItem cart = cartOpt.get();
            if (cart.getCount() > 1) {
                cart.setCount(cart.getCount() - 1);
                cartItemRepository.save(cart);
            } else {
                cartItemRepository.delete(cart);
            }
        }
    }

    private void delete(Optional<CartItem> cartOpt) {
        cartOpt.ifPresent(cartItemRepository::delete);
    }


    private ItemDto toItem(CartItem cartItem) {
        Product product = cartItem.getProduct();

        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(cartItem.getCount())
                .build();
    }
}
