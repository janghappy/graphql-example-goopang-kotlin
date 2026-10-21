package com.example.goopang.service

import com.example.goopang.entity.product.Clothing
import com.example.goopang.entity.product.Electronics
import com.example.goopang.entity.product.Product
import com.example.goopang.entity.product.ProductType
import com.example.goopang.exception.ClientException
import com.example.goopang.input.AddProductInput
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProductService {

	fun getProduct(productId: String): Product {
		return Database.products.firstOrNull { it.id == productId }
			?: throw ClientException("Product not found: $productId")
	}

	fun getProducts(): List<Product> {
		return Database.products
	}

	fun addProduct(addProductInput: AddProductInput): Product {
		val product = when (addProductInput.productType) {
			ProductType.ELECTRONICS -> {
				val warrantyPeriod = addProductInput.warrantyPeriod
					?: throw ClientException("warrantyPeriod is required for ELECTRONICS")
				Electronics(
					id = UUID.randomUUID().toString().substring(0, 5),
					name = addProductInput.name,
					price = addProductInput.price,
					warrantyPeriod = warrantyPeriod,
				)
			}

			ProductType.CLOTHING -> {
				val size = addProductInput.size
					?: throw ClientException("size is required for CLOTHING")
				Clothing(
					id = UUID.randomUUID().toString().substring(0, 5),
					name = addProductInput.name,
					price = addProductInput.price,
					size = size,
				)
			}
		}
		Database.products.add(product)

		return product
	}
}
