package com.smartbuildpc.backend.modules.cart.service;

import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.cart.entity.Cart;
import com.smartbuildpc.backend.modules.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartInitializer {

    private final CartRepository cartRepository;

    @Override
    @Transactional
    public Cart initializeCartForUser(User user) {
        Cart cart = Cart.builder()
                .user(user)
                .build();
        return cartRepository.save(cart);
    }
}
