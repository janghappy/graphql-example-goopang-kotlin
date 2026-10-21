# graphql-example-goopang

인프런 강의 [GraphQL: REST API를 대체할 기술](https://www.inflearn.com/course/graphql-rest-api%EB%A5%BC-%EB%8C%80%EC%B2%B4%ED%95%A0-%EA%B8%B0%EC%88%A0)의 실습 프로젝트(goopang 쇼핑몰)를 Spring Boot / Kotlin으로 따라 만드는 저장소입니다.

- 참고 저장소: [burger-2023/graphql-goopang-project](https://github.com/burger-2023/graphql-goopang-project) (`goopang-java`)

## 기술 스택

| 구분 | 버전 |
|---|---|
| Kotlin | 2.3.21 |
| Java (toolchain) | 17 |
| Spring Boot | 4.1.1 |
| Gradle | 9.8.1 (wrapper, Kotlin DSL) |
| Spring for GraphQL | `spring-boot-starter-graphql` |
| JSON | `jackson-module-kotlin` (Jackson 3) |
| 확장 스칼라 | `graphql-java-extended-scalars` 24.0 |

## 실행

```bash
./gradlew bootRun
```

- GraphQL 엔드포인트: `http://localhost:8081/graphql`
- Subscription(WebSocket): `ws://localhost:8081/graphql` (`graphql-transport-ws` 프로토콜)
- 테스트 클라이언트: [Altair GraphQL Client](https://altairgraphql.dev/)

## 프로젝트 구조

```
src/main/
├── kotlin/com/example/goopang/
│   ├── GoopangApplication.kt
│   ├── config/GraphQlConfig.kt        # 커스텀 스칼라(DateTime) 등록
│   ├── entity/                        # User, Product(Electronics, Clothing), Cart, CartItem, SearchResult
│   ├── exception/                     # ClientException, GraphQlExceptionHandler (메시지를 클라이언트에 전달)
│   ├── input/                         # AddUserInput, AddCartItemInput, DeleteCartItemInput, AddProductInput
│   ├── repository/Database.kt         # 인메모리 샘플 데이터
│   ├── service/                       # UserService, CartService, ProductService
│   └── resolver/                      # UserResolver, CartResolver, ProductResolver, SearchResolver (@QueryMapping, @MutationMapping, @SubscriptionMapping)
└── resources/
    ├── application.yaml               # 포트 8081, WebSocket 경로 /graphql, CORS(로컬 개발용 전체 허용)
    └── graphql/
        ├── schema.graphqls            # 루트 타입 선언, 공용 scalar
        ├── user/user.graphqls
        ├── product/product.graphqls
        ├── cart/cart.graphqls
        └── search/search.graphqls
```

스키마는 도메인별 폴더로 나누고, 각 도메인 파일에서 `extend type Query/Mutation/Subscription`으로 자기 오퍼레이션을 추가합니다.

## 문서

- [GraphQL 개요](docs/graphql-overview.md): GraphQL 개념, REST 비교, 생태계, 클라이언트/서버 문법, 구팡 기능
- [GraphQL 스키마 정리](docs/graphql-schema.md): Object Type, Custom Scalar, Query/Mutation/Subscription, Input, Union
- [Subscription 구현 정리](docs/graphql-subscription.md): @SubscriptionMapping, Sinks, WebSocket(graphql-transport-ws), Origin 검사, SSE
- [GraphQL 요청 샘플](docs/graphql-requests.md): 도메인별 query/mutation/subscription 샘플, union(`... on`), fragment, 변수
