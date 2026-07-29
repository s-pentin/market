package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, 1L, 2);

        when(cartItemRepository.findAll()).thenReturn(Flux.just(cartItem));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(product));

        StepVerifier.create(cartService.getAllProductsInCart())
                .assertNext(result -> {
                    assertThat(result.getItems()).hasSize(1);
                    assertThat(result.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(200));
                })
                .verifyComplete();
    }

    @Test
    void getAllProductsInCart_emptyCart_returnsZeroTotal() {
        when(cartItemRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(cartService.getAllProductsInCart())
                .assertNext(result -> {
                    assertThat(result.getItems()).isEmpty();
                    assertThat(result.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
                })
                .verifyComplete();
    }

    @Test
    void changeCount_plus_existingItem_incrementsCount() {
        CartItem cartItem = new CartItem(1L, 1L, 1);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.save(any())).thenReturn(Mono.just(cartItem));
        when(productRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeCount(1L, Action.PLUS))
                .verifyComplete();

        assertThat(cartItem.getCount()).isEqualTo(2);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void changeCount_plus_newItem_createsCartItemWithCountOne() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem saved = new CartItem(2L, 1L, 1);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.empty());
        when(productRepository.findById(1L)).thenReturn(Mono.just(product));
        when(cartItemRepository.save(any())).thenReturn(Mono.just(saved));

        StepVerifier.create(cartService.changeCount(1L, Action.PLUS))
                .verifyComplete();

        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void changeCount_minus_countGreaterThanOne_decrementsCount() {
        CartItem cartItem = new CartItem(1L, 1L, 3);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.save(any())).thenReturn(Mono.just(cartItem));

        StepVerifier.create(cartService.changeCount(1L, Action.MINUS))
                .verifyComplete();

        assertThat(cartItem.getCount()).isEqualTo(2);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void changeCount_minus_countEqualsOne_deletesItem() {
        CartItem cartItem = new CartItem(1L, 1L, 1);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeCount(1L, Action.MINUS))
                .verifyComplete();

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void changeCount_delete_deletesItem() {
        CartItem cartItem = new CartItem(1L, 1L, 5);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.just(cartItem));
        when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeCount(1L, Action.DELETE))
                .verifyComplete();

        verify(cartItemRepository).delete(cartItem);
    }
}
