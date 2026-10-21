package com.example.goopang.resolver

import com.example.goopang.entity.cart.Cart
import com.example.goopang.entity.user.User
import com.example.goopang.input.AddUserInput
import com.example.goopang.service.CartService
import com.example.goopang.service.UserService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class UserResolver(
	private val userService: UserService,
	private val cartService: CartService,
) {

	@QueryMapping
	fun getUser(
		@Argument userId: String,
	): User {
		return userService.getUser(userId)
	}

	@MutationMapping
	fun addUser(
		@Argument addUserInput: AddUserInput,
	): User {
		return userService.addUser(addUserInput)
	}

	// User.cart 필드를 요청했을 때만 실행된다 (getUser, addUser, search 등 User를 반환하는 모든 경로)
	@SchemaMapping(typeName = "User", field = "cart")
	fun cart(user: User): Cart {
		return cartService.getUserCart(user.id)
	}
}
