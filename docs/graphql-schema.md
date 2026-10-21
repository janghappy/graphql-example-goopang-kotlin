# GraphQL 스키마 정리

goopang 프로젝트 스키마(`src/main/resources/graphql/`)를 기준으로 GraphQL 타입 시스템을 정리합니다.

- [1. Object Type](#1-object-type)
- [2. Custom Scalar Type](#2-custom-scalar-type)
- [3. Query, Mutation, Subscription](#3-query-mutation-subscription)
- [4. Input, Union](#4-input-union)
- [부록. 스키마 파일 구성](#부록-스키마-파일-구성)

---

## 1. Object Type

클라이언트가 조회할 수 있는 데이터의 모양을 정의합니다. 필드마다 타입이 있고, 다른 Object Type을 필드로 가질 수 있어서 연관 데이터를 한 번의 요청으로 가져올 수 있습니다.

```graphql
# user/user.graphqls
type User {
    id: ID!
    name: String!
    email: String!
    cart: Cart!          # 다른 Object Type 참조
    createdAt: String!   # 추후 DateTime 커스텀 스칼라로 변경 예정
}

# cart/cart.graphqls
type Cart {
    id: ID!
    user: User!
    items: [CartItem!]!
    totalAmount: Float!
}
```

### 기본 스칼라와 수식어

| 타입 | 의미 |
|---|---|
| `ID` | 고유 식별자. 문자열로 직렬화됨 |
| `String`, `Int`, `Float`, `Boolean` | 기본 스칼라 |
| `T!` | null 불가 |
| `[T]` | 리스트 |
| `[T!]!` | 리스트 자체도 null 불가, 요소도 null 불가 |

### Interface

여러 Object Type이 공통으로 가져야 할 필드를 정의합니다. 구현 타입은 인터페이스의 필드를 모두 다시 선언해야 합니다.

```graphql
# product/product.graphqls
interface Product {
    id: ID!
    name: String!
    price: Float!
    productType: ProductType!
}

type Electronics implements Product {
    id: ID!
    name: String!
    price: Float!
    productType: ProductType!
    warrantyPeriod: String!   # 전자제품 전용 필드
}

type Clothing implements Product {
    id: ID!
    name: String!
    price: Float!
    productType: ProductType!
    size: String!             # 의류 전용 필드
}
```

`Product`를 반환하는 필드에서는 공통 필드는 바로 조회하고, 구현 타입 전용 필드는 인라인 프래그먼트로 조회합니다.

```graphql
query {
    getProducts {
        id
        name
        price
        __typename
        ... on Electronics { warrantyPeriod }
        ... on Clothing { size }
    }
}
```

### Enum

정해진 값 중 하나만 허용하는 타입입니다.

```graphql
enum ProductType {
    ELECTRONICS
    CLOTHING
}
```

---

## 2. Custom Scalar Type

기본 스칼라로 표현하기 어려운 값(날짜, 큰 정수, URL 등)을 위해 새 스칼라를 선언합니다.

> **현재 상태:** 이 프로젝트는 아직 커스텀 스칼라를 쓰지 않습니다. `User.createdAt`은 임시로 `String!`이고, `scalar DateTime` 선언과 구현 등록도 없습니다. 아래는 나중에 `DateTime`으로 바꿀 때 필요한 내용입니다.

도입할 때는 스키마에 스칼라를 선언하고 필드 타입을 바꿉니다.

```graphql
# schema.graphqls
scalar DateTime

# user/user.graphqls
type User {
    ...
    createdAt: DateTime!
}
```

### SDL은 이름만 선언한다

`scalar DateTime`은 "DateTime이라는 스칼라가 있다"는 선언일 뿐이고, **포맷이나 Kotlin/Java 타입은 정하지 않습니다.** 실제 변환 규칙은 서버 코드에서 `Coercing` 구현으로 정합니다. 구현을 등록하지 않으면 앱이 시작될 때 아래 오류가 납니다.

```
There is no scalar implementation for the named 'DateTime' scalar type
```

SDL에서 할 수 있는 것은 `@specifiedBy`로 따르는 명세 URL을 문서화하는 정도입니다. 서버가 이 값으로 검증하지는 않습니다.

```graphql
scalar DateTime @specifiedBy(url: "https://scalars.graphql.org/andimarek/date-time")
```

### 구현 등록: RuntimeWiringConfigurer

graphql-java는 **타입 정의**(`.graphqls` 파일)와 **런타임 와이어링**(스칼라 구현, DataFetcher, 타입 판별 등 서버 코드)을 합쳐서 실행 가능한 스키마를 만듭니다. Spring GraphQL은 시작할 때 `RuntimeWiringConfigurer` 빈을 모두 찾아 `RuntimeWiring.Builder`를 넘겨줍니다.

`ExtendedScalars.DateTime`을 쓰려면 의존성을 추가합니다. graphql-java 버전은 Spring Boot가 관리하며(현재 25.0), extended-scalars는 그에 맞춰 24.0을 씁니다.

```kotlin
// build.gradle.kts
implementation("com.graphql-java:graphql-java-extended-scalars:24.0")
```

```kotlin
// config/GraphQlConfig.kt
@Configuration
class GraphQlConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { it.scalar(ExtendedScalars.DateTime) }
}
```

`ExtendedScalars.DateTime`의 이름이 `"DateTime"`이라서 스키마의 `scalar DateTime`과 이름으로 연결됩니다. `@QueryMapping` 같은 어노테이션 컨트롤러도 내부적으로는 `AnnotatedControllerConfigurer`(역시 `RuntimeWiringConfigurer` 구현체)가 같은 빌더에 DataFetcher로 등록합니다.

### Coercing의 세 가지 변환

| 메서드 | 시점 | 방향 |
|---|---|---|
| `serialize` | 응답 | 서버 값 → JSON 문자열 |
| `parseValue` | 요청 변수(`variables`) | JSON 값 → 서버 값 |
| `parseLiteral` | 쿼리 본문에 직접 쓴 값 | AST 리터럴 → 서버 값 |

### ExtendedScalars.DateTime 규칙

> Java 버전 프로젝트에서 extended-scalars 22.0으로 확인한 결과입니다. 24.0에서는 다시 확인하지 않았습니다.

| 입력 | 결과 |
|---|---|
| `OffsetDateTime` 2026-10-21 11:50 +09:00 | `"2026-10-21T11:50:00.000+09:00"` |
| `ZonedDateTime` (Asia/Seoul, 나노초 포함) | `"2026-10-21T11:50:00.123+09:00"` (밀리초까지만) |
| 요청 문자열 `"2026-10-21T11:50:00Z"` | `OffsetDateTime`으로 변환 |
| `LocalDateTime` | 실패: OffsetDateTime으로 변환 불가 |
| `"2026-10-21 11:50:00"` | 실패: RFC 3339 형식 아님 |
| `"2026-10-21T11:50:00"` (오프셋 없음) | 실패: RFC 3339 형식 아님 |

- 포맷: RFC 3339, **타임존 오프셋 필수**
- 응답은 항상 밀리초 3자리까지 출력
- **엔티티 필드는 `OffsetDateTime`으로 선언**합니다(예: `val createdAt: OffsetDateTime`). `LocalDateTime`으로 만들면 조회할 때 실패합니다.

다른 포맷이 필요하면 `GraphQLScalarType.newScalar().name("DateTime").coercing(...)`으로 직접 구현해 같은 방식으로 등록하면 됩니다. 스키마 파일은 바꿀 필요가 없습니다.

---

## 3. Query, Mutation, Subscription

스키마의 진입점(루트 오퍼레이션 타입)입니다.

| 타입 | 용도 | REST에 대응하면 | 전송 방식 |
|---|---|---|---|
| `Query` | 조회 | GET | HTTP |
| `Mutation` | 생성·수정·삭제 | POST/PUT/DELETE | HTTP |
| `Subscription` | 이벤트 실시간 수신 | (없음) | WebSocket 등 |

### 루트 타입 선언과 extend type

루트 타입은 `schema.graphqls`에 필드 없이 선언하고, 각 도메인 파일에서 `extend type`으로 자기 오퍼레이션을 추가합니다. 도메인 하나의 타입과 오퍼레이션이 한 파일에 모이므로 기능을 추가하거나 찾을 때 파일 하나만 보면 됩니다.

```graphql
# schema.graphqls
type Query
type Mutation
type Subscription
```

```graphql
# user/user.graphqls
extend type Query {
    getUser(userId: ID!): User!
}

extend type Mutation {
    addUser(addUserInput: AddUserInput!): User!
}
```

### 전체 오퍼레이션

| 루트 | 필드 | 반환 | 정의 위치 |
|---|---|---|---|
| Query | `getUser(userId: ID!)` | `User!` | `user/user.graphqls` |
| Query | `getProducts` | `[Product!]!` | `product/product.graphqls` |
| Query | `getUserCart(userId: ID!)` | `Cart!` | `cart/cart.graphqls` |
| Query | `search(keyword: String!)` | `[SearchResult!]!` | `search/search.graphqls` |
| Mutation | `addUser(addUserInput: AddUserInput!)` | `User!` | `user/user.graphqls` |
| Mutation | `addProduct(addProductInput: AddProductInput!)` | `Product!` | `product/product.graphqls` |
| Mutation | `addCartItem(addCartItemInput: AddCartItemInput!)` | `Cart!` | `cart/cart.graphqls` |
| Mutation | `deleteCartItem(deleteCartItemInput: DeleteCartItemInput!)` | `Cart!` | `cart/cart.graphqls` |
| Subscription | `newProduct(productName: String)` | `Product!` | `product/product.graphqls` |

### 요청 예시

```graphql
query GetUser {
    getUser(userId: "1") {
        name
        createdAt
        cart {
            totalAmount
            items { quantity product { name price } }
        }
    }
}

mutation AddUser {
    addUser(addUserInput: { name: "홍길동", email: "hong@example.com" }) {
        id
        createdAt
    }
}

subscription NewProduct {
    newProduct(productName: "노트북") {
        id
        name
        price
    }
}
```

> Subscription을 실제로 동작시키려면 `spring-boot-starter-websocket` 의존성과 WebSocket 경로 설정이 필요합니다. 아직 추가하지 않았습니다.

---

## 4. Input, Union

### Input Type

Mutation 인자처럼 **클라이언트가 서버로 보내는** 구조화된 값을 정의합니다. Object Type과 비슷하지만 다음 차이가 있습니다.

- 출력용 `type`은 인자로 쓸 수 없고, 입력에는 `input`만 쓸 수 있습니다.
- `input`의 필드는 스칼라, enum, 다른 `input`만 가질 수 있습니다. Object Type, Interface, Union은 쓸 수 없습니다.

```graphql
# product/product.graphqls
input AddProductInput {
    name: String!
    price: Float!
    productType: ProductType!
    warrantyPeriod: String   # ELECTRONICS일 때만 사용 → 선택값
    size: String             # CLOTHING일 때만 사용 → 선택값
}

# cart/cart.graphqls
input AddCartItemInput {
    userId: ID!
    productId: ID!
    quantity: Int!
}

input DeleteCartItemInput {
    cartItemId: ID!
}
```

이름은 `{동작}{대상}Input` 형식으로 맞추고, Mutation 인자 이름은 input 타입 이름을 camelCase로 쓴 것과 같게 합니다(예: `addCartItemInput: AddCartItemInput!`).

### Union Type

**공통 필드가 없는** 서로 다른 Object Type 중 하나를 반환할 때 씁니다.

```graphql
# search/search.graphqls
union SearchResult = User | Electronics | Clothing

extend type Query {
    search(keyword: String!): [SearchResult!]!
}
```

Union 자체에는 필드가 없으므로 항상 인라인 프래그먼트로 타입별 필드를 지정합니다.

```graphql
query Search {
    search(keyword: "a") {
        __typename
        ... on User { name email }
        ... on Electronics { name price warrantyPeriod }
        ... on Clothing { name price size }
    }
}
```

### Interface와 Union 비교

| | Interface | Union |
|---|---|---|
| 공통 필드 | 있음 (구현 타입이 반드시 포함) | 없음 |
| 공통 필드 조회 | 프래그먼트 없이 바로 가능 | 불가, 항상 프래그먼트 필요 |
| 멤버 | Object Type이 `implements`로 참여 | `=`로 나열한 Object Type만 |
| 이 프로젝트 | `Product` (Electronics, Clothing) | `SearchResult` (User, Electronics, Clothing) |

---

## 부록. 스키마 파일 구성

```
src/main/resources/graphql/
├── schema.graphqls          # type Query, type Mutation, type Subscription
├── user/user.graphqls       # User, AddUserInput + getUser, addUser
├── product/product.graphqls # Product, Electronics, Clothing, ProductType, AddProductInput
│                            # + getProducts, addProduct, newProduct
├── cart/cart.graphqls       # Cart, CartItem, AddCartItemInput, DeleteCartItemInput
│                            # + getUserCart, addCartItem, deleteCartItem
└── search/search.graphqls   # SearchResult + search
```

- Spring GraphQL은 기본적으로 `classpath:graphql/**/` 아래를 하위 폴더까지 읽습니다.
- 스키마 파일은 Spring GraphQL 기본 확장자인 `.graphqls`를 써서 별도 설정이 필요 없습니다(기본값은 `.graphqls`, `.gqls`).
