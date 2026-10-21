package com.example.goopang.service

import com.example.goopang.entity.user.User
import com.example.goopang.exception.ClientException
import com.example.goopang.input.AddUserInput
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class UserService(
	private val cartService: CartService,
) {

	fun getUsers(): List<User> {
		return Database.users
	}

	fun getUser(userId: String): User {
		return Database.users.firstOrNull { it.id == userId }
			?: throw ClientException("User not found: $userId")
	}

	fun addUser(addUserInput: AddUserInput): User {
		return User(
			id = UUID.randomUUID().toString().substring(0, 5),
			name = addUserInput.name,
			email = addUserInput.email,
			createdAt = OffsetDateTime.now(),
		).also { user -> Database.users.add(user) }
			.also { user -> cartService.addUserCart(user) }
	}
}
