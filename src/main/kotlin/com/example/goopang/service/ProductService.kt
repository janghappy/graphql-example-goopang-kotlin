package com.example.goopang.service

import com.example.goopang.entity.product.Product
import com.example.goopang.exception.ClientException
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service

@Service
class ProductService {

	fun getProduct(productId: String): Product {
		return Database.products.firstOrNull { it.id == productId }
			?: throw ClientException("Product not found: $productId")
	}
}
