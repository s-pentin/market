package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void getAllProductsInCart_returnsItemsAndTotalCost() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", 100L);
        CartItem cartItem = new CartItem(1L, product, 2);
        when(cartItemRepository.findAll()).thenReturn(List.of(cartItem));

        ProductsInCart result = cartService.getAllProductsInCart();

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalCost()).isEqualTo(200L);
    }

    @Test
    void getAllProductsInCart_emptyCart_returnsZeroTotal() {
        when(cartItemRepository.findAll()).thenReturn(List.of());

        ProductsInCart result = cartService.getAllProductsInCart();

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalCost()).isEqualTo(0L);
    }

    @Test
    void changeCount_plus_existingItem_incrementsCount() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        CartItem cartItem = new CartItem(1L, product, 1);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeCount(1L, Action.PLUS);

        assertThat(cartItem.getCount()).isEqualTo(2);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void changeCount_plus_newItem_createsCartItemWithCountOne() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        cartService.changeCount(1L, Action.PLUS);

        verify(cartItemRepository).save(argThat(item ->
                item.getProduct().equals(product) && item.getCount() == 1));
    }

    @Test
    void changeCount_minus_countGreaterThanOne_decrementsCount() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        CartItem cartItem = new CartItem(1L, product, 3);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeCount(1L, Action.MINUS);

        assertThat(cartItem.getCount()).isEqualTo(2);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void changeCount_minus_countEqualsOne_deletesItem() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        CartItem cartItem = new CartItem(1L, product, 1);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeCount(1L, Action.MINUS);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void changeCount_delete_deletesItem() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        CartItem cartItem = new CartItem(1L, product, 5);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        cartService.changeCount(1L, Action.DELETE);

        verify(cartItemRepository).delete(cartItem);
    }
}
