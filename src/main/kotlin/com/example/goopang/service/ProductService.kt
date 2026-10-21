package com.example.goopang.service

import com.example.goopang.entity.product.Clothing
import com.example.goopang.entity.product.Electronics
import com.example.goopang.entity.product.Product
import com.example.goopang.entity.product.ProductType
import com.example.goopang.exception.ClientException
import com.example.goopang.input.AddProductInput
import com.example.goopang.repository.Database
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

@Service
class ProductService {

	// 구독 이후에 추가된 상품만 전달한다 (구독자가 없을 때 발행된 상품은 버퍼링하지 않음)
	private val productSink =
		Sinks
			.many()
			.multicast()
			.directBestEffort<Product>()

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

		productSink.tryEmitNext(product)

		return product
	}

	fun messageFlux(productName: String?): Flux<Product> {
		return productSink.asFlux()
			.let { flux ->
				if (productName != null) {
					flux.filter { it.name.contains(productName) }
				} else {
					flux
				}
			}
	}
}
