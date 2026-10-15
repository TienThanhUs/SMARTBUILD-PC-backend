package com.smartbuildpc.backend.modules.cart.service;

import com.smartbuildpc.backend.modules.auth.entity.User;
import com.smartbuildpc.backend.modules.cart.entity.Cart;

public interface CartInitializer {

    Cart initializeCartForUser(User user);
}
