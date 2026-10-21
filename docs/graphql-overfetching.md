# 오버페칭과 @SchemaMapping

"GraphQL은 오버페칭을 해결한다"는 말이 이 프로젝트에서 실제로 어디까지 맞는지, `getUser` → `cart`를 예로 확인하고 `@SchemaMapping`으로 개선한 과정을 정리합니다.

- [오버페칭의 두 층위](#오버페칭의-두-층위)
- [적용 전: 상위 resolver에서 cart를 미리 채움](#적용-전-상위-resolver에서-cart를-미리-채움)
- [적용 후: @SchemaMapping으로 필드 resolver 분리](#적용-후-schemamapping으로-필드-resolver-분리)
- [측정 결과 비교](#측정-결과-비교)
- [N+1 문제](#n1-문제)
- [정리](#정리)

---

## 오버페칭의 두 층위

| 층위 | 의미 | GraphQL이 자동으로 해결하나 |
|---|---|---|
| **네트워크 (응답)** | 클라이언트가 필요 없는 필드까지 응답으로 받음 | **예.** 요청한 필드만 응답에 담김 |
| **서버 내부 (조회)** | 응답에 쓰이지 않을 데이터를 서버가 조회·계산함 | **아니요.** resolver 설계에 달림 |

graphql-java는 **요청된 필드에 대해서만 그 필드의 DataFetcher(resolver)를 실행**합니다. 하지만 비용이 드는 조회를 상위 resolver에서 미리 해 버리면, 필드 단위 실행의 이점을 얻지 못합니다.

---

## 적용 전: 상위 resolver에서 cart를 미리 채움

```kotlin
// entity/user/User.kt
data class User(...) : SearchResult {
	var cart: Cart? = null
}

// service/UserService.kt
fun getUsers(): List<User> {   // search에서 사용
	return Database.users
		.onEach { user -> user.cart = cartService.getUserCart(user.id) }   // User마다 항상 실행
}

fun getUser(userId: String): User {
	return Database.users.firstOrNull { it.id == userId }
		?.also { user -> user.cart = cartService.getUserCart(userId) }   // 항상 실행
		?: throw ClientException("User not found: $userId")
}
```

`getUser { id name }`만 요청해도:

1. `UserResolver.getUser` → `UserService.getUser` 호출
2. **`cartService.getUserCart`가 항상 실행됨** (장바구니 항목 필터링, `totalAmount` 계산)
3. graphql-java가 반환된 `User`에서 요청된 필드(`id`, `name`)만 골라 응답 생성. `User.cart`는 별도 resolver가 없어 기본 DataFetcher가 이미 채워진 프로퍼티를 읽기만 함

**응답 크기는 줄었지만, 서버 내부에서는 cart 조회가 그대로 일어납니다(서버 내부 오버페칭).**

`search`는 더 심합니다. 검색 결과의 User가 N명이면, 클라이언트가 `cart`를 요청하지 않아도 `getUserCart`가 N번 실행됩니다.

---

## 적용 후: @SchemaMapping으로 필드 resolver 분리

`User.cart`를 **필드 resolver**로 분리하고, 엔티티와 서비스에서 cart를 채우던 코드를 없앴습니다.

```kotlin
// resolver/UserResolver.kt
@Controller
class UserResolver(
	private val userService: UserService,
	private val cartService: CartService,
) {
	...

	// User.cart 필드를 요청했을 때만 실행된다 (getUser, addUser, search 등 User를 반환하는 모든 경로)
	@SchemaMapping(typeName = "User", field = "cart")
	fun cart(user: User): Cart {
		return cartService.getUserCart(user.id)
	}
}
```

```kotlin
// entity/user/User.kt: cart 필드 제거
data class User(
	val id: String,
	val name: String,
	val email: String,
	val createdAt: OffsetDateTime,
) : SearchResult

// service/UserService.kt: cart를 채우지 않음
fun getUsers(): List<User> {
	return Database.users
}

fun getUser(userId: String): User {
	return Database.users.firstOrNull { it.id == userId }
		?: throw ClientException("User not found: $userId")
}
```

- 엔티티는 DB 모델처럼 자기 데이터만 갖고, 연관 데이터는 resolver가 필요할 때 가져오는 구조가 됩니다.

### @SchemaMapping은 전역 설정이다

`@SchemaMapping(typeName = "User", field = "cart")`는 특정 Query/Mutation에 붙는 것이 아니라, **스키마의 `User` 타입의 `cart` 필드**에 DataFetcher를 등록합니다. 애플리케이션 전체에서 `User.cart` 필드의 값은 이 메서드 하나가 만듭니다.

- `@QueryMapping fun getUser(...)`는 `@SchemaMapping(typeName = "Query", field = "getUser")`의 축약형입니다. 루트 타입(`Query`)이냐 일반 타입(`User`)이냐만 다를 뿐 같은 방식입니다.
- `field`를 생략하면 메서드 이름으로 필드를 찾습니다. 메서드 이름이 `cart`이므로 `@SchemaMapping(typeName = "User")`와 같습니다.

### 응답 트리 어디서든 실행된다

응답 트리 어디든 **`User` 객체가 나오고 그 아래에서 `cart`를 요청하면** 실행됩니다. User를 직접 반환하는 mapping뿐 아니라, union 결과나 다른 타입의 필드로 중첩된 User도 마찬가지입니다.

| 요청 | User가 나오는 위치 | 실행 여부 |
|---|---|---|
| `getUser { cart { ... } }` | Query 결과 | ✅ |
| `addUser { cart { ... } }` | Mutation 결과 | ✅ |
| `search { ... on User { cart { ... } } }` | union 결과 목록 안 | ✅ (User 수만큼) |
| `getUserCart { user { cart { ... } } }` | `Cart.user` 필드 안 (중첩) | ✅ |
| `getUser { id name }` | Query 결과 | ❌ (cart 미요청) |

실행 순서는 다음과 같습니다.

1. `getUser`가 `User` 객체를 반환합니다.
2. graphql-java가 요청된 하위 필드를 하나씩 처리합니다.
   - `id`, `name`: 별도 resolver가 없으므로 기본 DataFetcher가 `user.id`, `user.name` 프로퍼티를 읽습니다.
   - `cart`: 등록된 resolver가 있으므로 `cart(user)`를 호출합니다. 첫 번째 파라미터로 상위 객체(source), 즉 그 `User`가 들어옵니다.
3. `cart(user)`가 반환한 `Cart`가 응답의 `cart` 자리에 쓰이고, 그 `Cart`의 하위 필드(`items`, `totalAmount`)를 같은 방식으로 처리합니다.

`User` 객체를 수정해서 "채우는" 것이 아니라, 필드 값을 **반환**합니다. 호출은 User 객체 하나마다 한 번씩 일어납니다.

### 중복 설정 시 기동 시점 에러

같은 `타입.필드`에 매핑을 두 개 선언하면 **애플리케이션이 기동되지 않습니다.** 요청 처리 중이 아니라 시작할 때 실패합니다.

다른 컨트롤러에 `@SchemaMapping(typeName = "User", field = "cart")`를 하나 더 선언하고 기동했을 때의 에러입니다(직접 확인).

```
java.lang.IllegalStateException: Ambiguous mapping. Cannot map 'userResolver' method
public Cart UserResolver.cart(User)
: There is already 'tmpDuplicateResolver' bean method
User.cart -> public Cart TmpDuplicateResolver.duplicateCart(User) mapped.
```

- Spring for GraphQL은 기동할 때 모든 `@Controller`의 매핑을 모아 `타입.필드` 단위로 등록합니다. 메서드 이름이 달라도, 다른 클래스에 있어도 같은 `타입.필드`면 충돌합니다.
- 둘 중 하나가 조용히 선택되지 않고 바로 실패하므로, 실수를 일찍 발견할 수 있습니다.
- `@QueryMapping`도 같습니다. 같은 이름의 Query 매핑이 두 개면 같은 에러가 납니다(`Query.getUser`도 `타입.필드` 하나).
- 경로별로 다르게 동작해야 한다면 `DataFetchingEnvironment`로 분기할 수는 있지만, 보통은 스키마에 필드를 따로 정의하는 편이 명확합니다.

---

## 측정 결과 비교

`CartService.getUserCart`에 임시 로그를 넣고 같은 요청을 보내 호출 횟수를 셌습니다(측정 후 로그는 제거).

| 요청 | 적용 전 | 적용 후 |
|---|---|---|
| `getUser { id name }` | **1회** (불필요한 조회) | **0회** |
| `getUser { id name cart { totalAmount } }` | 1회 | 1회 |
| `search(keyword: "e") { ... on User { name } }` | **2회** (User 2명, 불필요한 조회) | **0회** |
| `search(keyword: "e") { ... on User { name cart { totalAmount } } }` | 2회 | 2회 |
| `addUser { id name }` | 0회 (`addUserCart`로 빈 cart 생성) | 0회 |
| `addUser { id cart { id totalAmount } }` | 0회 | 1회 |
| `getUserCart { totalAmount user { name } }` | 1회 | 1회 |

- cart를 요청하지 않으면 cart 조회가 실행되지 않습니다. 서버 내부 오버페칭이 사라졌습니다.
- `addUser`는 사용자 생성 시 빈 장바구니를 만드는 `addUserCart`를 계속 호출합니다. 이는 조회가 아니라 데이터 생성이므로 그대로 둡니다.

---

## N+1 문제

측정 결과에서 `search`가 User 2명을 반환하고 각각의 `cart`를 요청하자 `getUserCart`가 2번 실행됐습니다. User가 N명이면 N번 실행됩니다.

- 부모 목록 조회 1번 + 자식 조회 N번이라 **N+1 문제**라고 부릅니다.
- **`@SchemaMapping`이 만든 문제는 아닙니다.** 적용 전 구조도 `getUsers`에서 User마다 `getUserCart`를 호출하므로 똑같이 N+1이었고, cart를 요청하지 않아도 N번 실행됐습니다(측정 결과 2회). `@SchemaMapping`은 N+1을 만들지도 없애지도 않고, **필요할 때만 실행되게** 할 뿐입니다.
- DB를 쓰면 쿼리가 N번 나가므로, 여러 건을 한 번에 조회하는 방법이 필요합니다.

---

## 정리

| 구조 | 응답(네트워크) | 서버 내부 조회 |
|---|---|---|
| 적용 전: 서비스에서 cart를 미리 채움 | 요청한 필드만 | cart를 항상 조회 |
| **적용 후: `@SchemaMapping`** | 요청한 필드만 | cart를 요청했을 때만 조회 (목록이면 N회) |

GraphQL이 기본으로 해결하는 오버페칭은 **클라이언트가 받는 데이터(네트워크)** 쪽입니다. 서버 내부까지 필요한 것만 조회하려면, 비용이 드는 연관 데이터를 상위 resolver에서 미리 채우지 말고 **필드 resolver로 분리**해야 합니다.
