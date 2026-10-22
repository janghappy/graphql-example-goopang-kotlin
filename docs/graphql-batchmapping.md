# N+1 문제와 @BatchMapping

`User.cart`를 `@SchemaMapping` 필드 resolver로 분리한 뒤 남은 N+1 문제를 `@BatchMapping`으로 확인한 과정을 정리합니다. 필드 resolver 분리와 오버페칭은 [오버페칭과 @SchemaMapping](graphql-overfetching.md)을 참고하세요.

- [N+1 문제](#n1-문제)
- [확인용 필드 batchCart 추가](#확인용-필드-batchcart-추가)
- [@SchemaMapping과의 차이](#schemamapping과의-차이)
- [측정 결과](#측정-결과)
- [정리](#정리)

---

## N+1 문제

`@SchemaMapping` 필드 resolver는 상위 객체 하나마다 한 번씩 호출됩니다. `search`가 User 2명을 반환하고 각각의 `cart`를 요청하면 `getUserCart`가 2번 실행됩니다. User가 N명이면 N번입니다.

- 부모 목록 조회 1번 + 자식 조회 N번이라 **N+1 문제**라고 부릅니다.
- DB를 쓰면 쿼리가 N번 나가므로, 여러 건을 한 번에 조회하는 방법이 필요합니다.

---

## 확인용 필드 batchCart 추가

같은 `타입.필드`에는 매핑을 하나만 둘 수 있습니다(두 개면 기동 시점 `Ambiguous mapping` 에러). 그래서 기존 `User.cart`(`@SchemaMapping`)는 그대로 두고, 같은 값을 반환하는 **확인용 필드 `User.batchCart`**를 추가해 `@BatchMapping`을 붙였습니다. 같은 요청에서 `cart`와 `batchCart`를 바꿔 가며 호출 횟수를 비교할 수 있습니다.

```graphql
# user/user.graphqls
type User {
    id: ID!
    name: String!
    email: String!
    cart: Cart!
    batchCart: Cart!   # @BatchMapping 확인용: cart와 같은 값을 여러 User에 대해 한 번에 조회
    createdAt: DateTime!
}
```

```kotlin
// resolver/UserResolver.kt
// User.cart 필드를 요청했을 때만 실행된다 (getUser, addUser, search 등 User를 반환하는 모든 경로)
@SchemaMapping(typeName = "User", field = "cart")
fun cart(user: User): Cart {
	return cartService.getUserCart(user.id)
}

// @BatchMapping 확인용: User.cart와 같은 값을, 한 요청 안의 User들을 모아 한 번에 조회한다 (N+1 방지)
@BatchMapping(typeName = "User", field = "batchCart")
fun batchCart(users: List<User>): Map<User, Cart> {
	val cartsByUserId = cartService.getUserCarts(users.map { it.id })
		.associateBy { it.user.id }

	return users.associateWith { user ->
		cartsByUserId[user.id] ?: throw ClientException("Cart not found for user: ${user.id}")
	}
}
```

```kotlin
// service/CartService.kt
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
```

---

## @SchemaMapping과의 차이

| | `@SchemaMapping` (`cart`) | `@BatchMapping` (`batchCart`) |
|---|---|---|
| 파라미터 | 상위 객체 하나 (`user: User`) | 상위 객체 목록 (`users: List<User>`) |
| 반환 | 값 하나 (`Cart`) | 상위 객체별 값 (`Map<User, Cart>`) |
| 호출 횟수 | User마다 1번 (N번) | 한 요청에서 1번 |
| 내부 동작 | DataFetcher 직접 실행 | DataLoader에 키(User)를 모았다가 일괄 실행 |

- 동작 원리: graphql-java가 응답 트리를 처리하면서 `User.batchCart`를 만날 때마다 바로 실행하지 않고 DataLoader에 User를 등록해 둡니다. 같은 단계의 필드 처리가 끝나면 모인 User 목록으로 메서드를 **한 번** 호출하고, 반환된 `Map`에서 User별 값을 꺼내 각 자리에 넣습니다.
- 반환 `Map`의 키는 파라미터로 받은 User 객체입니다. 키가 빠지면 해당 User의 값은 null이 되고, 스키마가 `Cart!`이므로 에러가 납니다. 그래서 cart가 없는 User는 명시적으로 `ClientException`을 던집니다.
- `@SchemaMapping`과 마찬가지로 `User.batchCart` 필드에 대한 **전역 설정**입니다. 같은 `타입.필드`에 `@SchemaMapping`과 `@BatchMapping`을 함께 선언하면 `Ambiguous mapping` 기동 에러가 납니다(직접 확인).

---

## 측정 결과

`getUserCart`/`getUserCarts`에 임시 로그를 넣고, 같은 요청을 `cart`와 `batchCart`로 바꿔 보내 호출을 확인했습니다(측정 후 로그 제거).

| 요청 | `cart` (@SchemaMapping) | `batchCart` (@BatchMapping) |
|---|---|---|
| `getUser(userId: "1") { name ... }` | 1회 (`getUserCart(1)`) | 1회 (`getUserCarts([1])`) |
| `search(keyword: "e") { ... on User { name ... } }` | **2회** (`getUserCart(1)`, `getUserCart(2)`) | **1회** (`getUserCarts([1, 2])`) |
| `search(keyword: "e") { ... on User { name } }` (cart 미요청) | 0회 | 0회 |

한 요청 안의 **서로 다른 필드**에 있는 User도 함께 모입니다.

```graphql
{
  a: getUser(userId: "1") { batchCart { totalAmount } }
  b: getUser(userId: "2") { batchCart { totalAmount } }
  c: getUserCart(userId: "2") { user { batchCart { id } } }
}
```

| | `cart`로 요청 | `batchCart`로 요청 |
|---|---|---|
| 필드 resolver 호출 | 3회 (`getUserCart` 1, 2, 2) | **1회** (`getUserCarts([1, 2])`) |
| `c`의 Query 자체 호출 | `getUserCart(2)` 1회 | `getUserCart(2)` 1회 |

- `a`, `b`, `c.user`의 요청 3건이 `getUserCarts([1, 2])` **1회**로 처리됐습니다.
- `b`와 `c.user`는 같은 User(Jane)라서 키가 중복 제거됐습니다. `cart`(@SchemaMapping)는 같은 User라도 매번 호출합니다.

---

## 정리

| 필드 | 매핑 | 요청하지 않으면 | 목록(N명)에서 요청하면 |
|---|---|---|---|
| `User.cart` | `@SchemaMapping` | 조회 안 함 | N회 |
| `User.batchCart` | `@BatchMapping` | 조회 안 함 | **1회** |

`@SchemaMapping`으로 "요청했을 때만 조회"를 얻고, `@BatchMapping`으로 "여러 건을 한 번에 조회"를 더합니다. 둘 다 필드 단위의 전역 설정이므로, 같은 필드에는 하나만 선언할 수 있습니다.
