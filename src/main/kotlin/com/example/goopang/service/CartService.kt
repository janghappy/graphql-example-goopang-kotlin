package com.example.goopang.service

import com.example.goopang.entity.cart.Cart
import com.example.goopang.entity.user.User
import com.example.goopang.exception.ClientException
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CartService {

	fun getUserCart(userId: String): Cart {
		return Database.carts.firstOrNull { it.user.id == userId }
			?.also { cart -> cart.items = Database.cartItems.filter { it.cart.id == cart.id } }
			?.also { cart -> cart.totalAmount = cart.items.sumOf { it.product.price * it.quantity } }
			?: throw ClientException("Cart not found for user: $userId")
	}

	fun addUserCart(user: User): Cart {
		return Cart(
			id = UUID.randomUUID().toString().substring(0, 5),
			user = user,
		).also { cart -> Database.carts.add(cart) }
	}
}
