# Subscription 구현 정리

`newProduct` 구독을 기준으로 Spring for GraphQL의 Subscription 구현 방식을 정리합니다. 스키마 전반은 [GraphQL 스키마 정리](graphql-schema.md)를 참고하세요.

- [@SubscriptionMapping](#subscriptionmapping)
- [이벤트 발행: Sinks](#이벤트-발행-sinks)
- [전송 방식: WebSocket (graphql-transport-ws)](#전송-방식-websocket-graphql-transport-ws)
- [WebSocket 핸드셰이크와 Origin 검사](#websocket-핸드셰이크와-origin-검사)
- [HTTP로 Subscription을 보내면](#http로-subscription을-보내면)

---

## @SubscriptionMapping

Query/Mutation은 요청 한 번에 응답 한 번이지만, Subscription은 **요청 한 번에 응답이 여러 번** 옵니다. 그래서 resolver는 값 하나가 아니라 값의 스트림(`Publisher`, 보통 Reactor `Flux`)을 반환합니다. Spring for GraphQL은 이 `Flux`를 구독하고, 원소가 나올 때마다 클라이언트에 메시지로 보냅니다.

```graphql
# product/product.graphqls
extend type Subscription {
    newProduct(productName: String): Product!
}
```

```kotlin
// resolver/ProductResolver.kt
@SubscriptionMapping
fun newProduct(
	@Argument productName: String?,
): Flux<Product> {
	return productService.messageFlux(productName)
}
```

- 메서드 이름이 `Subscription` 타입의 필드 이름(`newProduct`)과 연결됩니다. `@QueryMapping`, `@MutationMapping`과 같은 규칙입니다.
- 반환 타입은 `Flux<Product>`이지만, 스키마의 반환 타입은 원소 하나의 타입(`Product!`)으로 적습니다. `[Product!]!`가 아닙니다.
- 원소마다 클라이언트가 요청한 필드만 골라서 보내는 것도 Query와 같습니다.

## 이벤트 발행: Sinks

Mutation(`addProduct`)에서 생긴 이벤트를 구독자에게 전달하는 다리 역할은 Reactor `Sinks`가 합니다.

```kotlin
// service/ProductService.kt
private val productSink =
	Sinks
		.many()
		.multicast()
		.directBestEffort<Product>()

fun addProduct(addProductInput: AddProductInput): Product {
	...
	Database.products.add(product)
	productSink.tryEmitNext(product)   // 구독자들에게 발행
	return product
}

fun messageFlux(productName: String?): Flux<Product> {
	return productSink.asFlux()
		.let { flux ->
			if (productName != null) {
				flux.filter { it.name.contains(productName) }   // 구독 인자로 필터링
			} else {
				flux
			}
		}
}
```

```
addProduct ──▶ productSink.tryEmitNext(product)
                     │  (multicast: 모든 구독자에게)
          ┌──────────┴──────────┐
     구독자 A (전체)       구독자 B (productName: "book")
     filter 없음           .filter { "book" 포함 }
          │                     │
     next 메시지            조건 맞을 때만 next 메시지
```

- `asFlux()`를 부를 때마다 구독자별 `Flux`가 생기므로, 구독 인자(`productName`)에 따른 필터를 구독자마다 다르게 걸 수 있습니다.
- 구독자가 하나도 없을 때 `tryEmitNext`를 부르면 발행에 실패(`FAIL_ZERO_SUBSCRIBER`)할 뿐 예외는 나지 않습니다. 새 상품 알림이므로 그대로 버려도 됩니다.

`Sinks.many()`의 종류에 따라 "구독 전에 발행된 값"을 다루는 방식이 다릅니다.

| 방식 | 구독 전 발행된 값 | 이 프로젝트에서 |
|---|---|---|
| `multicast().directBestEffort()` | 버림. 구독 이후 값만 받음 | **사용** |
| `multicast().onBackpressureBuffer()` | 구독자가 없는 동안 버퍼에 쌓았다가 **처음 연결한 구독자에게** 전달 | 강의 코드 방식. 새 알림에 지난 상품이 섞여서 쓰지 않음 |
| `replay().limit(n)` | 최근 n개를 새 구독자마다 다시 전달 | "최근 n개 + 이후 실시간"이 필요할 때 |

실제로 `onBackpressureBuffer()`로 구독 전에 `galaxy book`을 추가하고 구독자 두 명을 연결했더니, 먼저 연결된 쪽만 `galaxy book`을 받았습니다. `directBestEffort()`로 바꾼 뒤에는 두 구독자 모두 구독 이후 추가된 상품만 받았습니다.

> `Database`처럼 `Sinks`도 서버 프로세스 메모리에 있습니다. 서버를 여러 대 띄우면 다른 서버에서 추가된 상품은 전달되지 않으므로, 그때는 Redis Pub/Sub이나 Kafka 같은 메시지 브로커가 필요합니다.

## 전송 방식: WebSocket (graphql-transport-ws)

Subscription은 서버가 원할 때 메시지를 보내야 하므로 보통 WebSocket을 씁니다. 필요한 설정은 다음과 같습니다.

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

```yaml
# application.yaml
spring:
  graphql:
    websocket:
      path: /graphql            # ws://localhost:8081/graphql
    cors:
      allowed-origin-patterns: "*"   # 로컬 개발용
      allowed-methods: "*"
```

Spring for GraphQL은 [`graphql-transport-ws`](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md) 프로토콜만 지원합니다. 클라이언트(Altair 등)에서는 Subscription type을 `graphql-ws`로 골라야 합니다. 예전 Apollo 방식인 `subscriptions-transport-ws`로는 연결되지 않습니다.

실제로 주고받은 메시지 순서입니다(`←`가 서버 응답).

```
→ {"type":"connection_init"}
← {"type":"connection_ack","payload":{}}
→ {"id":"1","type":"subscribe","payload":{"query":"subscription { newProduct { id name } }"}}
   ... addProduct 발생 ...
← {"id":"1","type":"next","payload":{"data":{"newProduct":{"id":"8a1cd","name":"mac book pro"}}}}
← {"id":"1","type":"next","payload":{"data":{"newProduct":{"id":"2de61","name":"hoodie"}}}}
→ {"id":"1","type":"complete"}       # 클라이언트가 구독 종료
```

| 상황 | 서버 동작 (직접 확인) |
|---|---|
| 정상 구독 | 이벤트마다 `next` |
| 쿼리 검증 실패 (없는 필드 등) | `next`에 `errors`를 담아 보낸 뒤 `complete` |
| 클라이언트가 `complete` 전송 | 해당 구독 종료, 이후 메시지 없음 |
| 클라이언트가 `ping` 전송 | `pong` 응답 |

## WebSocket 핸드셰이크와 Origin 검사

`cors` 설정이 없으면 Altair에서 "Server Error"가 나며 연결되지 않았습니다. 핸드셰이크 응답은 `403`이었습니다.

| 핸드셰이크의 Origin 헤더 | cors 설정 없음 | cors 설정 후 |
|---|---|---|
| 없음 (curl 등) | 101 | 101 |
| 서버와 같은 출처 | 101 | 101 |
| 다른 출처 (Altair, 브라우저 확장 등) | **403** | 101 |

- HTTP의 CORS는 **브라우저**가 응답 헤더를 보고 막습니다. 브라우저가 아닌 클라이언트에는 영향이 없습니다.
- WebSocket은 브라우저가 CORS를 적용하지 않습니다. 그래서 **서버**가 핸드셰이크의 `Origin` 헤더를 직접 검사합니다(CSWSH 방어). Spring은 기본으로 같은 출처만 허용합니다.
- Altair는 브라우저나 Electron(Chromium) 위에서 동작하므로 `Origin`을 보내고, 이 검사에 걸립니다.
- Spring Boot는 HTTP CORS와 WebSocket 출처 검사를 같은 `spring.graphql.cors.*` 설정으로 관리합니다.

## HTTP로 Subscription을 보내면

| 요청 | 결과 (직접 확인) |
|---|---|
| `POST /graphql`, 일반 JSON | `{"data":{"upstreamPublisher":{...}}}`처럼 `Flux` 객체가 그대로 직렬화된 잘못된 응답. 쓰면 안 됨 |
| `POST /graphql`, `Accept: text/event-stream` (SSE) | `event:next` / `data:{...}` 형태로 이벤트가 정상 전달됨 |

WebSocket을 쓸 수 없는 환경이라면 SSE로 구독할 수 있습니다.
