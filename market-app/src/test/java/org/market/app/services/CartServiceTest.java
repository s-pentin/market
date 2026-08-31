package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.Action;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void getAllProductsInCart_returnsItemsAndTotalCost() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, USER_ID, 1L, 2);

        when(cartItemRepository.findAllByUserId(USER_ID)).thenReturn(Flux.just(cartItem));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(product));

        StepVerifier.create(cartService.getAllProductsInCart(USER_ID))
                .assertNext(result -> {
                    assertThat(result.getItems()).hasSize(1);
                    assertThat(result.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(200));
                })
                .verifyComplete();
    }

    @Test
    void getAllProductsInCart_emptyCart_returnsZeroTotal() {
        when(cartItemRepository.findAllByUserId(USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(cartService.getAllProductsInCart(USER_ID))
                .assertNext(result -> {
                    assertThat(result.getItems()).isEmpty();
                    assertThat(result.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
                })
                .verifyComplete();
    }

    @Test
    void getAllProductsInCart_onlyReturnsItemsForGivenUser() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, USER_ID, 1L, 2);
        when(cartItemRepository.findAllByUserId(USER_ID)).thenReturn(Flux.just(cartItem));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(product));

        StepVerifier.create(cartService.getAllProductsInCart(USER_ID))
                .assertNext(cart -> assertThat(cart.getItems()).hasSize(1))
                .verifyComplete();

        verify(cartItemRepository, never()).findAllByUserId(2L);
    }

    @Test
    void changeCount_plus_existingProduct_usesAtomicIncrementOrInsert() {
        when(productRepository.existsById(1L)).thenReturn(Mono.just(true));
        when(cartItemRepository.incrementOrInsert(USER_ID, 1L)).thenReturn(Mono.just(1));

        StepVerifier.create(cartService.changeCount(USER_ID, 1L, Action.PLUS))
                .verifyComplete();

        verify(cartItemRepository).incrementOrInsert(USER_ID, 1L);
    }

    @Test
    void changeCount_plus_unknownProduct_throwsProductNotFound() {
        when(productRepository.existsById(1L)).thenReturn(Mono.just(false));

        StepVerifier.create(cartService.changeCount(USER_ID, 1L, Action.PLUS))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void changeCount_minus_countAboveOne_decrements() {
        when(cartItemRepository.decrementIfAboveOne(USER_ID, 1L)).thenReturn(Mono.just(1));

        StepVerifier.create(cartService.changeCount(USER_ID, 1L, Action.MINUS))
                .verifyComplete();

        verify(cartItemRepository).decrementIfAboveOne(USER_ID, 1L);
        verify(cartItemRepository, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    void changeCount_minus_countWasOne_deletesItem() {
        when(cartItemRepository.decrementIfAboveOne(USER_ID, 1L)).thenReturn(Mono.just(0));
        when(cartItemRepository.deleteByUserIdAndProductId(USER_ID, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeCount(USER_ID, 1L, Action.MINUS))
                .verifyComplete();

        verify(cartItemRepository).deleteByUserIdAndProductId(USER_ID, 1L);
    }

    @Test
    void changeCount_delete_deletesItem() {
        when(cartItemRepository.deleteByUserIdAndProductId(USER_ID, 1L)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeCount(USER_ID, 1L, Action.DELETE))
                .verifyComplete();

        verify(cartItemRepository).deleteByUserIdAndProductId(USER_ID, 1L);
    }
}
