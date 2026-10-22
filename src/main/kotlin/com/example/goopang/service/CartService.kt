package com.example.goopang.service

import com.example.goopang.entity.cart.Cart
import com.example.goopang.entity.cart.CartItem
import com.example.goopang.entity.user.User
import com.example.goopang.exception.ClientException
import com.example.goopang.input.AddCartItemInput
import com.example.goopang.input.DeleteCartItemInput
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CartService(
	private val productService: ProductService,
) {

	fun getUserCart(userId: String): Cart {
		return Database.carts.firstOrNull { it.user.id == userId }
			?.also { cart -> cart.items = Database.cartItems.filter { it.cart.id == cart.id } }
			?.also { cart -> cart.totalAmount = cart.items.sumOf { it.product.price * it.quantity } }
			?: throw ClientException("Cart not found for user: $userId")
	}

	// 여러 User의 Cart를 한 번에 조회한다 (User.batchCart @BatchMapping용)
	fun getUserCarts(userIds: List<String>): List<Cart> {
		val carts = Database.carts.filter { it.user.id in userIds }
		val itemsByCartId = Database.cartItems
			.filter { item -> carts.any { it.id == item.cart.id } }
			.groupBy { it.cart.id }

		return carts.onEach { cart ->
			cart.items = itemsByCartId[cart.id].orEmpty()
			cart.totalAmount = cart.items.sumOf { it.product.price * it.quantity }
		}
	}

	fun addUserCart(user: User): Cart {
		return Cart(
			id = UUID.randomUUID().toString().substring(0, 5),
			user = user,
		).also { cart -> Database.carts.add(cart) }
	}

	fun addCartItem(addCartItemInput: AddCartItemInput): Cart {
		CartItem(
			id = UUID.randomUUID().toString().substring(0, 5),
			quantity = addCartItemInput.quantity,
			product = productService.getProduct(addCartItemInput.productId),
			cart = getUserCart(addCartItemInput.userId),
		).also { Database.cartItems.add(it) }

		return getUserCart(addCartItemInput.userId)
	}

	fun deleteCartItem(deleteCartItemInput: DeleteCartItemInput): Cart {
		val cartItem = Database.cartItems.firstOrNull { it.id == deleteCartItemInput.cartItemId }
			?: throw ClientException("Cart item not found: ${deleteCartItemInput.cartItemId}")
		Database.cartItems.remove(cartItem)

		return getUserCart(cartItem.cart.user.id)
	}
}
