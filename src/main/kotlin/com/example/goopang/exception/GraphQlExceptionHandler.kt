package com.example.goopang.exception

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.execution.ErrorType
import org.springframework.web.bind.annotation.ControllerAdvice

@ControllerAdvice
class GraphQlExceptionHandler {

	@GraphQlExceptionHandler
	fun handleClientException(ex: ClientException, env: DataFetchingEnvironment): GraphQLError {
		return GraphqlErrorBuilder.newError(env)
			.errorType(ErrorType.BAD_REQUEST)
			.message(ex.message)
			.build()
	}
}
