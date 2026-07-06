package org.market.app.repositories;

import org.market.app.models.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByProductId(Long productId);
    List<CartItem> findAllByProductIdIn(Collection<Long> productIds);
}
