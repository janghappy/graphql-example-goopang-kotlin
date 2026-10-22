# GraphQL 요청 샘플

goopang 서버에 보낼 수 있는 요청을 도메인별로 정리합니다. [Altair GraphQL Client](https://altairgraphql.dev/)에 그대로 붙여 넣어 실행할 수 있습니다.

- [요청 종류와 전송 방식](#요청-종류와-전송-방식)
- [User](#user)
- [Search (union, `... on`)](#search-union--on)
- [Cart (fragment)](#cart-fragment)
- [Product](#product)
- [Subscription (WebSocket)](#subscription-websocket)
- [작성 팁](#작성-팁)

---

## 요청 종류와 전송 방식

GraphQL 요청은 맨 앞의 키워드로 종류를 구분합니다.

| 종류 | 용도 | 이 프로젝트의 요청 | 전송 방식 |
|---|---|---|---|
| `query` | 조회 | `getUser`, `getUserCart`, `getProducts`, `search` | HTTP `POST http://localhost:8081/graphql` |
| `mutation` | 생성·수정·삭제 | `addUser`, `addCartItem`, `deleteCartItem`, `addProduct` | HTTP `POST http://localhost:8081/graphql` |
| `subscription` | 서버 이벤트 실시간 수신 | `newProduct` | **WebSocket `ws://localhost:8081/graphql`** |

- `query`와 `mutation`은 요청 한 번에 응답 한 번입니다. HTTP로 보냅니다.
- `subscription`은 한 번 구독하면 이벤트가 생길 때마다 응답을 계속 받습니다. 그래서 **WebSocket(ws)으로 연결해야 합니다.** 자세한 내용은 [Subscription (WebSocket)](#subscription-websocket)을 보세요.
- 키워드 뒤의 이름(`query getUser`의 `getUser`)은 오퍼레이션 이름입니다. 생략할 수 있지만, 한 문서에 여러 요청을 적을 때는 꼭 붙여야 합니다([작성 팁](#작성-팁) 참고).

---

## User

### getUser: 사용자 조회

```graphql
query getUser {
  getUser(userId: "1") {
    id
    name
    email
    cart {
      id
      user {
        id
        name
        email
        createdAt
      }
      items {
        id
        product {
          id
          name
          price
          productType
        }
        quantity
      }
      totalAmount
    }
    createdAt
  }
}
```

- `userId`: 샘플 데이터의 사용자는 `"1"`(Burger)과 `"2"`(Jane)입니다. `addUser`로 추가한 사용자는 응답의 `id`(예: `"84dec"`)를 넣으면 됩니다. id는 실행할 때마다 새로 만들어집니다.
- `cart`, `user`, `items`, `product`는 객체 타입이라 하위 필드를 반드시 적어야 합니다. 빼먹으면 `Subselection required for type 'Cart!'` 검증 에러가 납니다.

### addUser: 사용자 추가

```graphql
mutation addUser {
  addUser(
    addUserInput: {
      name: "pizza"
      email: "pizza@goopang.com"
    }
  ) {
    id
    name
    email
  }
}
```

- 추가된 사용자에게는 빈 장바구니가 함께 만들어집니다.
- 데이터는 메모리에만 저장되므로 서버를 재시작하면 사라집니다.

### batchCart: @BatchMapping 확인용

`User.batchCart`는 `User.cart`와 **같은 값**을 반환하는 확인용 필드입니다. `cart`는 `@SchemaMapping`(User마다 조회), `batchCart`는 `@BatchMapping`(한 요청의 User를 모아 한 번에 조회)으로 구현되어 있습니다. 응답은 같고 **서버 내부의 조회 횟수만 다릅니다.**

여러 User가 나오는 `search`에서 차이가 드러납니다.

```graphql
query searchWithCart {
  search(keyword: "e") {
    ... on User {
      name
      cart { totalAmount }        # User마다 조회 → User 2명이면 2회
    }
  }
}

query searchWithBatchCart {
  search(keyword: "e") {
    ... on User {
      name
      batchCart { totalAmount }   # 모아서 한 번에 조회 → 1회
    }
  }
}
```

별칭(alias)으로 여러 필드에 흩어진 User도 한 번에 모이는지 확인할 수 있습니다.

```graphql
query multiUserBatchCart {
  a: getUser(userId: "1") { name batchCart { totalAmount } }
  b: getUser(userId: "2") { name batchCart { totalAmount } }
  c: getUserCart(userId: "2") { user { name batchCart { id } } }
}
```

- `a:`, `b:`, `c:`는 **별칭**입니다. 같은 필드(`getUser`)를 인자만 바꿔 한 요청에서 여러 번 부를 때, 응답 키가 겹치지 않도록 이름을 붙입니다. 응답은 `{"a": {...}, "b": {...}, "c": {...}}` 형태로 옵니다.
- `batchCart` 요청 3건(`a`, `b`, `c.user`)이 서버에서 한 번(`getUserCarts([1, 2])`)으로 처리됩니다. `b`와 `c.user`는 같은 User라 중복도 제거됩니다. 같은 요청을 `cart`로 바꾸면 3번 조회합니다.
- 응답만으로는 차이가 보이지 않습니다. 호출 횟수 측정 결과는 [N+1 문제와 @BatchMapping](graphql-batchmapping.md)을 참고하세요.

---

## Search (union, `... on`)

### search: 통합 검색

```graphql
query search {
  search(
    keyword: "J"
  ) {
    __typename
    ... on Product {
      id
      name
      productType
    }
    ... on User {
      id
      name
    }
  }
}
```

응답 예시:

```json
{
  "data": {
    "search": [
      { "__typename": "Clothing", "id": "5", "name": "Jeans", "productType": "CLOTHING" },
      { "__typename": "User", "id": "2", "name": "Jane" }
    ]
  }
}
```

**union은 `... on 타입` 형태(인라인 프래그먼트)로 조회합니다.**

- `search`의 반환 타입 `SearchResult`는 `union SearchResult = User | Electronics | Clothing`입니다.
- union 자체에는 필드가 없으므로 `search { id name }`처럼 바로 쓸 수 없습니다. 반드시 `... on 타입 { 필드 }`로 타입별 필드를 지정합니다.
- `... on Product`처럼 **인터페이스**를 지정할 수도 있습니다. `Electronics`와 `Clothing`이 모두 `Product`를 구현하므로 두 타입에 한 번에 적용됩니다. 타입별 전용 필드가 필요하면 `... on Electronics { warrantyPeriod }`, `... on Clothing { size }`처럼 구체 타입을 지정합니다.
- `__typename`을 함께 요청하면 각 결과가 실제로 어떤 타입인지 알 수 있습니다.
- `keyword`는 대소문자를 구분합니다(`"j"`로는 `Jane`이 검색되지 않음).

---

## Cart (fragment)

`getUserCart`, `addCartItem`, `deleteCartItem`은 모두 `Cart`를 반환합니다. 같은 필드 목록을 세 번 쓰는 대신 **fragment**로 한 번 정의하고 재사용합니다.

```graphql
fragment cartFields on Cart {
  id
  user {
    id
    name
    email
    createdAt
  }
  items {
    id
    product {
      id
      name
      price
      productType
    }
    quantity
  }
  totalAmount
}
```

- `fragment 이름 on 타입 { 필드 }`로 정의하고, 사용할 곳에서 `...이름`으로 펼칩니다.
- fragment는 요청 문서 안에 함께 있어야 합니다. 아래 요청을 보낼 때 이 fragment 정의도 같이 붙여 넣으세요.
- 앞의 `... on Product`(인라인 프래그먼트)는 이름 없이 그 자리에서 타입 조건만 거는 것이고, `fragment cartFields`는 이름을 붙여 여러 곳에서 재사용하는 것입니다.

### getUserCart: 장바구니 조회

```graphql
query getUserCart {
  getUserCart(userId: "1") {
    ...cartFields
  }
}
```

### addCartItem: 장바구니에 상품 추가

```graphql
mutation addCartItem {
  addCartItem(
    addCartItemInput: {
      userId: "1"
      productId: "1"
      quantity: 10
    }
  ) {
    ...cartFields
  }
}
```

- 응답의 `items` 마지막 항목의 `id`가 새로 추가된 장바구니 항목 id입니다. `deleteCartItem`에 사용합니다.

### deleteCartItem: 장바구니 항목 삭제

```graphql
mutation deleteCartItem {
  deleteCartItem(
    deleteCartItemInput: {
      cartItemId: "123"
    }
  ) {
    ...cartFields
  }
}
```

- `cartItemId`만 보내면 됩니다. 서버가 항목의 소유자를 찾아 그 사용자의 장바구니를 반환합니다.
- 샘플 데이터의 장바구니 항목 id는 `"1"`~`"6"`입니다. `"123"`처럼 없는 id를 보내면 다음 에러가 반환됩니다.

```json
{
  "errors": [{
    "message": "Cart item not found: 123",
    "path": ["deleteCartItem"],
    "extensions": { "classification": "BAD_REQUEST" }
  }],
  "data": null
}
```

---

## Product

### getProducts: 상품 목록 조회

```graphql
query getProducts {
  getProducts {
    id
    name
    price
    productType
  }
}
```

- `Product`는 인터페이스이므로 공통 필드(`id`, `name`, `price`, `productType`)는 바로 조회할 수 있습니다.
- 타입별 전용 필드는 `... on Electronics { warrantyPeriod }`, `... on Clothing { size }`로 조회합니다.

### addProduct: 상품 추가

```graphql
mutation addProduct {
  addProduct(
    addProductInput: {
      name: "iphone 23"
      price: 10.1
      productType: ELECTRONICS
      warrantyPeriod: "10years"
    }
  ) {
    id
    name
    price
    productType
  }
}
```

- `productType`은 enum이라 따옴표 없이 `ELECTRONICS`, `CLOTHING`으로 씁니다.
- `ELECTRONICS`는 `warrantyPeriod`, `CLOTHING`은 `size`가 필요합니다. 빠지면 `BAD_REQUEST` 에러(`warrantyPeriod is required for ELECTRONICS`)가 납니다.
- 상품이 추가되면 `newProduct` 구독자에게 알림이 갑니다.

---

## Subscription (WebSocket)

### newProduct: 새 상품 알림 구독

```graphql
subscription newProduct {
  newProduct {
    id
    name
    price
    productType
  }
}
```

이름으로 필터링하려면 `productName`을 넘깁니다. 이름에 해당 문자열이 포함된 상품만 받습니다.

```graphql
subscription newBookProduct {
  newProduct(productName: "book") {
    id
    name
  }
}
```

**subscription은 HTTP가 아니라 WebSocket(ws)으로 요청해야 합니다.**

| 항목 | 값 |
|---|---|
| 주소 | `ws://localhost:8081/graphql` |
| 프로토콜 | `graphql-transport-ws` |
| Altair 설정 | Subscription URL: `ws://localhost:8081/graphql`, Subscription type: `graphql-ws` |

- Altair의 Subscription type에서 `subscriptions-transport-ws`(예전 Apollo 방식)를 고르면 연결되지 않습니다.
- 구독은 **구독한 이후에** 추가된 상품만 받습니다. 구독을 연결해 둔 상태에서 다른 탭으로 `addProduct`를 실행해 확인하세요.
- 일반 HTTP POST로 subscription을 보내면 이벤트가 전달되지 않고 잘못된 응답이 옵니다. HTTP로 받아야 한다면 `Accept: text/event-stream`(SSE) 헤더를 붙여야 합니다.

자세한 동작은 [Subscription 구현 정리](graphql-subscription.md)를 참고하세요.

---

## 작성 팁

### 한 문서에 여러 요청을 적을 때

이 문서의 샘플처럼 여러 요청을 한 번에 붙여 넣으면 각 요청에 이름(`query getUser`, `mutation addUser` 등)이 있어야 합니다. Altair는 실행할 요청을 고르게 해 주고, 그 이름을 `operationName`으로 서버에 보냅니다.

이름이 없거나 `operationName`을 지정하지 않고 여러 요청을 보내면 서버가 거부합니다.

```
Must provide operation name if query contains multiple operations.
```

### 변수 사용

값을 쿼리에 직접 쓰는 대신 변수로 분리할 수 있습니다. Altair 하단의 Variables 창에 JSON으로 값을 넣습니다.

```graphql
mutation addUser($input: AddUserInput!) {
  addUser(addUserInput: $input) {
    id
    name
  }
}
```

```json
{
  "input": { "name": "pizza", "email": "pizza@goopang.com" }
}
```

### 자주 보는 에러

| 에러 | 원인 |
|---|---|
| `Subselection required for type 'Cart!' of field 'cart'` | 객체 타입 필드에 하위 필드를 적지 않음 |
| `Field 'xxx' in type 'Product' is undefined` | 인터페이스/union에서 구체 타입 전용 필드를 `... on` 없이 요청 |
| `... is missing required fields '[email]'` | input의 필수(`!`) 필드 누락 |
| `BAD_REQUEST` + 메시지 | 서버에서 거부 (없는 id, 필수값 누락 등). 메시지에 이유가 있음 |
| `INTERNAL_ERROR for <id>` | 예상하지 못한 서버 오류. 서버 로그에서 같은 id로 원인 확인 |
