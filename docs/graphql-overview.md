# GraphQL 개요

GraphQL이 무엇인지, 클라이언트와 서버에서 각각 어떻게 쓰는지 정리합니다. 이 프로젝트 스키마를 기준으로 한 타입별 상세 설명은 [GraphQL 스키마 정리](graphql-schema.md)를 참고하세요.

- [1. GraphQL이란](#1-graphql이란)
- [2. REST API와 비교](#2-rest-api와-비교)
- [3. 생태계와 도구](#3-생태계와-도구)
- [4. 타입 시스템 기초](#4-타입-시스템-기초)
- [5. 클라이언트 GraphQL](#5-클라이언트-graphql)
- [6. 서버 GraphQL](#6-서버-graphql)
- [7. 구팡 프로젝트 기능](#7-구팡-프로젝트-기능)

---

## 1. GraphQL이란

클라이언트가 **필요한 데이터의 모양을 쿼리로 적어 보내면** 서버가 정확히 그 모양으로 응답합니다.

### 역사

| 연도 | 내용 |
|---|---|
| 2012 | Facebook이 모바일 앱(뉴스피드)을 개발하면서 내부용으로 만듦 |
| 2015 | 명세와 레퍼런스 구현(graphql-js)을 오픈소스로 공개 |
| 2018 | Linux Foundation 산하 GraphQL Foundation으로 이관 |
| 2021 | Netflix가 GraphQL 서버 프레임워크 DGS(Domain Graph Service)를 오픈소스로 공개 |

---

## 2. REST API와 비교

| | REST API | GraphQL |
|---|---|---|
| 설계 중심 | 리소스 | 스키마(타입 그래프) |
| 엔드포인트 | 리소스·용도별로 여러 개 (`/users/1`, `/users/1/cart`) | **단일 엔드포인트** (`/graphql`) |
| 응답 모양 | 서버가 정함 | 클라이언트가 쿼리로 정함 |
| 여러 리소스 조회 | 여러 번 요청 | 한 번의 요청 |
| HTTP 메서드 | GET/POST/PUT/DELETE | 주로 POST (Query는 GET도 가능) |
| 문서화 | Swagger 등을 따로 관리 | 스키마 자체가 문서. Introspection으로 조회 가능 |

### Over-fetching과 Under-fetching

- **Over-fetching**: 필요 없는 데이터까지 받는 문제입니다. 예를 들어 화면에는 사용자 이름만 필요한데 `GET /users/1`이 주소, 전화번호 등 모든 필드를 돌려줍니다.
- **Under-fetching**: 한 번의 요청으로는 필요한 데이터가 부족해서 여러 번 요청해야 하는 문제입니다. 예를 들어 사용자와 장바구니 상품을 보여주려면 `GET /users/1` 다음에 `GET /users/1/cart`, 그다음 상품마다 `GET /products/{id}`를 호출해야 합니다.

GraphQL은 필요한 필드만, 연관 데이터까지 한 번에 요청해서 두 문제를 모두 해결합니다.

```graphql
query {
    getUser(userId: "1") {
        name
        cart {
            items {
                quantity
                product { name price }
            }
        }
    }
}
```

---

## 3. 생태계와 도구

### 명세, 엔진, 프레임워크의 관계

```
GraphQL (명세)
  └─ GraphQL Java (Java 구현 엔진)
       ├─ Spring for GraphQL (Spring 공식)
       └─ Netflix DGS (Netflix)
```

- **GraphQL Java**: 스키마 파싱, 검증, 실행을 담당하는 Java 엔진입니다.
- **Spring for GraphQL**: GraphQL Java 위에 Spring 통합(어노테이션 컨트롤러, 자동 설정, 테스트 도구)을 얹은 공식 프로젝트입니다.
- **Netflix DGS**: GraphQL Java를 기반으로 발전했고, 지금은 Spring for GraphQL과 통합되어 함께 동작할 수 있습니다.

위 그림은 개념적인 관계일 뿐, 모든 구성 요소가 위에서 아래로만 호출되는 계층 구조라는 뜻은 아닙니다. Spring Boot 프로젝트에서 GraphQL을 쓰는 방법은 두 가지입니다.

1. **Spring for GraphQL을 직접 사용**: `spring-boot-starter-graphql`. 이 프로젝트는 이 방식입니다.
2. **Netflix DGS를 추가해서 사용**: DGS의 어노테이션과 코드 생성 기능 등을 함께 씁니다.

### 도구

| 구분 | 도구 | 설명 |
|---|---|---|
| 클라이언트(테스트) | [Altair GraphQL Client](https://altairgraphql.dev/) | 데스크톱 앱. 강의에서 사용 |
| 클라이언트(테스트) | GraphiQL | 웹 IDE. Spring에서는 `spring.graphql.graphiql.enabled=true`로 `/graphiql`에서 사용 가능 |
| 시각화 | [GraphQL Voyager](https://apis.guru/graphql-voyager/) | 스키마를 타입 관계 그래프로 시각화 |
| 스키마 작성 | SDL (Schema Definition Language) | 스키마를 정의하는 언어. 이 프로젝트의 `*.graphqls` 파일 |

---

## 4. 타입 시스템 기초

### Scalar 타입

더 이상 쪼갤 수 없는 **리프(leaf) 값**입니다. 쿼리의 가장 끝에 오는 필드는 항상 스칼라나 Enum입니다.

| 타입 | 설명 |
|---|---|
| `Int` | 32비트 정수 |
| `Float` | 배정밀도 부동소수 |
| `String` | UTF-8 문자열 |
| `Boolean` | `true` / `false` |
| `ID` | 고유 식별자. 응답에서는 항상 문자열로 직렬화되고, 입력으로는 문자열과 정수를 모두 받음 |

기본 스칼라로 표현하기 어려운 값은 `Date`, `DateTime` 같은 **Custom Scalar**로 선언합니다. → [Custom Scalar 상세](graphql-schema.md#2-custom-scalar-type)

### Enum, Non-Null, List

```graphql
enum ProductType { ELECTRONICS CLOTHING }   # Enum: 정해진 값 중 하나

type Cart {
    id: ID!                 # Non-Null: null 불가
    items: [CartItem!]!     # List: 리스트도, 요소도 null 불가
}
```

| 표기 | 리스트가 null | 요소가 null |
|---|---|---|
| `[T]` | 가능 | 가능 |
| `[T!]` | 가능 | 불가 |
| `[T]!` | 불가 | 가능 |
| `[T!]!` | 불가 | 불가 |

---

## 5. 클라이언트 GraphQL

### 연산 종류

| 연산 | 용도 |
|---|---|
| `query` | 서버에서 데이터 조회 |
| `mutation` | 데이터 생성·수정·삭제 |
| `subscription` | 서버 이벤트를 실시간으로 받음 (주로 WebSocket) |

```graphql
query {
    getProducts {
        id
        name
        price
        productType
    }
}
```

연산에 이름과 변수를 붙이면 재사용과 디버깅이 쉬워집니다.

```graphql
query GetUser($userId: ID!) {
    getUser(userId: $userId) { name email }
}
# variables: { "userId": "1" }
```

### Fragment

자주 쓰는 필드 묶음에 이름을 붙여 재사용합니다.

```graphql
fragment ProductFields on Product {
    id
    name
    price
}

query {
    getProducts { ...ProductFields }
}
```

**Inline Fragment**는 Interface나 Union에서 실제 구현 타입별로 필드를 지정할 때 씁니다.

```graphql
query {
    getProducts {
        name
        ... on Electronics { warrantyPeriod }
        ... on Clothing { size }
    }
}
```

### Directive

필드나 프래그먼트에 조건을 붙입니다. 명세에 기본으로 들어 있는 지시자는 다음과 같습니다.

| 지시자 | 사용 위치 | 의미 |
|---|---|---|
| `@include(if: Boolean!)` | 쿼리 | 조건이 참이면 필드를 포함 |
| `@skip(if: Boolean!)` | 쿼리 | 조건이 참이면 필드를 제외 |
| `@deprecated(reason: String)` | 스키마 | 더 이상 쓰지 않는 필드·enum 값 표시 |
| `@specifiedBy(url: String!)` | 스키마 | 커스텀 스칼라가 따르는 명세 URL 표시 |

```graphql
query GetUser($userId: ID!, $isAnonymous: Boolean!, $isMine: Boolean!) {
    getUser(userId: $userId) {
        name
        email @skip(if: $isAnonymous)
        cart @include(if: $isMine) { totalAmount }
    }
}
```

### Meta Field

모든 타입에서 조회할 수 있는, `__`로 시작하는 특수 필드입니다.

| 메타 필드 | 위치 | 설명 |
|---|---|---|
| `__typename` | 모든 Object Type | 실제 타입 이름. Interface나 Union의 구현 타입을 구분할 때 사용 |
| `__schema` | Query 루트 | 스키마 전체 정보 |
| `__type(name: String!)` | Query 루트 | 특정 타입 정보 |

`__Field`, `__Type`, `__EnumValue` 등은 메타 필드가 아니라, Introspection 결과를 표현하는 **타입** 이름입니다.

### Introspection

스키마 정보를 조회하는 쿼리입니다. Altair, GraphiQL의 자동완성과 문서 탭, GraphQL Voyager가 이 기능으로 스키마를 읽어 갑니다.

```graphql
query {
    __type(name: "User") {
        name
        fields {
            name
            type { name kind }
        }
    }
}
```

> 운영 환경에서는 스키마 노출을 막으려고 Introspection을 끄기도 합니다(Spring: `spring.graphql.schema.introspection.enabled=false`).

---

## 6. 서버 GraphQL

### 구성 요소

| 구성 요소 | 역할 | Spring for GraphQL에서 |
|---|---|---|
| Schema | 타입과 연산 정의 | `src/main/resources/graphql/**/*.graphqls` |
| Resolver | 필드 값을 실제로 가져오는 함수 | `@QueryMapping`, `@MutationMapping`, `@SchemaMapping` 메서드 (내부적으로는 DataFetcher) |
| Runtime Wiring | 스키마와 Java 구현 연결 (스칼라, 리졸버, 타입 판별) | `RuntimeWiringConfigurer` |
| Middleware | 요청 전후 공통 처리 (인증, 로깅, 헤더 처리 등) | `WebGraphQlInterceptor`, GraphQL Java `Instrumentation` |

### 요청 처리 과정

```
HTTP 요청 (POST /graphql)
  → 파싱: 쿼리 문자열을 AST(Document)로 변환
  → 검증: 스키마 기준으로 필드, 타입, 인자, 변수를 검사
  → 실행: 루트 필드부터 하위 필드로 내려가며 Resolver 호출
  → 응답: { "data": ..., "errors": [...] } JSON
```

### 오류 응답

GraphQL은 요청 하나에 여러 필드를 담아 보내고, 서버는 필드마다 Resolver를 따로 실행합니다. 그래서 일부 필드만 실패하는 상황이 생기고, 오류를 돌려주는 방식도 REST와 다릅니다.

#### REST와 비교

REST는 요청 하나에 리소스 하나라서 성공 아니면 실패이고, 상태 코드로 구분합니다.

```
GET /users/999   → 404 Not Found
GET /products    → 200 OK
```

#### 부분 성공

GraphQL에서는 아래처럼 한 요청에 두 가지를 함께 물어볼 수 있습니다.

```graphql
query {
    user: getUser(userId: "999") { name }   # 없는 사용자 → Resolver에서 예외
    products: getProducts { name }          # 정상
}
```

서버는 실패한 필드만 `null`로 두고 나머지 결과는 정상적으로 담습니다. 실패 이유는 `errors`에 `path`(실패한 필드 위치)와 함께 넣습니다. (`getUser`가 null을 허용한다고 가정한 예시입니다. Non-Null인 경우는 아래 [null 전파](#non-null과-null-전파) 참고)

```json
{
  "data": {
    "user": null,
    "products": [{ "name": "노트북" }, { "name": "티셔츠" }]
  },
  "errors": [
    {
      "message": "사용자를 찾을 수 없습니다",
      "path": ["user"]
    }
  ]
}
```

- **부분 성공**: 한 응답 안에 성공한 데이터(`data`)와 실패 정보(`errors`)가 함께 들어 있다는 뜻입니다.
- **HTTP 200**: 성공과 실패가 섞여 있어서 404, 500 같은 상태 코드 하나로는 표현할 수 없습니다. 서버는 "GraphQL 응답을 정상적으로 만들어 보냈다"는 의미로 200을 돌려줍니다.
- 그래서 클라이언트는 **HTTP 상태 코드가 아니라 응답 본문에 `errors`가 있는지**로 성공 여부를 판단해야 합니다.

#### Non-Null과 null 전파

Non-Null(`!`) 필드가 실패하면 그 자리에 null을 넣을 수 없습니다. 그래서 **null을 허용하는 가장 가까운 상위 필드까지 null이 올라갑니다**. 루트 필드까지 모두 Non-Null이면 `data` 전체가 null이 됩니다.

이 프로젝트의 `getUser(userId: ID!): User!`는 반환 타입이 Non-Null이므로, 위 요청을 보내면 정상인 `products`까지 받지 못합니다.

```json
{
  "data": null,
  "errors": [{ "message": "사용자를 찾을 수 없습니다", "path": ["user"] }]
}
```

"없으면 null"을 허용하려면 반환 타입을 `User`(느낌표 없음)로 두는 것도 설계상 선택지입니다. Non-Null은 클라이언트가 null 처리를 하지 않아도 되게 해 주지만, 오류 하나가 주변 데이터까지 지우는 범위가 커집니다.

#### 실행 전 오류

쿼리 문법이 틀렸거나, 스키마에 없는 필드를 요청했거나, 변수 타입이 맞지 않으면 **파싱이나 검증 단계에서 실패해서 Resolver를 하나도 실행하지 않습니다**. 이때는 `data` 없이 `errors`만 돌아옵니다.

```json
{
  "errors": [
    {
      "message": "Validation error (FieldUndefined@[getUser/nickname]) : Field 'nickname' in type 'User' is undefined",
      "locations": [{ "line": 1, "column": 26 }],
      "extensions": { "classification": "ValidationError" }
    }
  ]
}
```

위 응답은 이 프로젝트 서버에 `{ getUser(userId: "1") { nickname } }`을 보내서 실제로 받은 결과이고, 이 경우에도 HTTP 상태 코드는 200이었습니다. `locations`는 오류가 난 쿼리 위치(줄, 열), `extensions.classification`은 오류 분류입니다.

### 스키마 문법 (SDL)

| 분류 | 종류 |
|---|---|
| 스칼라 | `Int`, `Float`, `String`, `Boolean`, `ID`, Custom Scalar |
| 열거 | `enum` |
| 출력 타입 | `type`(Object Type), `interface`, `union` |
| 입력 타입 | `input` |
| 루트 연산 | `Query`, `Mutation`, `Subscription` |

각 타입의 정의와 이 프로젝트에서의 사용 예는 [GraphQL 스키마 정리](graphql-schema.md)에 있습니다.

---

## 7. 구팡 프로젝트 기능

| 도메인 | 기능 | 연산 |
|---|---|---|
| 유저 | 조회, 추가 | `getUser`, `addUser` |
| 제품 | 조회, 추가 | `getProducts`, `addProduct` |
| 장바구니 | 조회, 추가, 삭제 | `getUserCart`, `addCartItem`, `deleteCartItem` |
| 알림 | 새 제품 실시간 알림 | `newProduct` (Subscription) |
| 검색 | 키워드로 유저·제품 검색 | `search` |

### 도메인 관계

```
User 1 ── 1 Cart 1 ── N CartItem N ── 1 Product
                                           ├─ Electronics
                                           └─ Clothing
```

- 유저 한 명은 장바구니 하나를 가집니다.
- 장바구니에는 여러 개의 장바구니 항목(CartItem)이 들어갑니다.
- 장바구니 항목 하나는 제품 하나와 수량을 가집니다.
- 제품(Product)은 인터페이스이고, 전자제품(Electronics)과 의류(Clothing)가 구현합니다.
