package com.flamingo.qa.graphql;

import com.flamingo.qa.api.graphql.GraphQlClient;
import com.flamingo.qa.api.graphql.GraphQlError;
import com.flamingo.qa.api.graphql.GraphQlRequest;
import com.flamingo.qa.api.graphql.GraphQlResponse;
import com.flamingo.qa.api.graphql.model.Movie;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * GraphQL failure handling, and the answer to the brief's question of whether
 * errors arrive as HTTP 200 or as an errors array.
 *
 * <p>On Hygraph it depends on <em>when</em> the request fails, which matches the
 * GraphQL-over-HTTP specification:
 *
 * <pre>
 *   phase          example                          HTTP   data              errors
 *   execution      unknown id                       200    {movie: null}     absent
 *   execution      mutation on a missing record     200    {field: null}     present
 *   authorization  mutation on an existing record   403    null              present
 *   request        syntax error                     400    null              present
 *   request        unknown field / bad variables    400    null              present
 * </pre>
 *
 * <p>A request that fails parsing or validation never executes, so there is no
 * data and the status is 400. A request that executes returns 200 and reports
 * problems per field, which is how GraphQL delivers partial results. A mutation
 * that reaches a real record is stopped by a permission check that fails the
 * whole request with 403.
 *
 * <p>The two mutation rows differ only in whether the target id exists, so the
 * response is an existence oracle. On this API that discloses nothing, because
 * every id is already publicly listable.
 *
 * <p>Deliberately invalid documents are kept inline, beside the reason they are
 * invalid, rather than as {@code .graphql} files.
 */
@Epic("Hygraph GraphQL")
@Feature("Error handling")
class GraphQlNegativeTest {

    /** A delete on the public, read-only content API. Always refused. */
    private static final String DELETE_MOVIE =
            "mutation DeleteMovie($id: ID!) { deleteMovie(where: { id: $id }) { id } }";

    private final GraphQlClient graphQl = new GraphQlClient();

    // ---------------------------------------------------------------
    // Execution phase: the request runs, so the status is 200
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Execution-phase errors")
    @DisplayName("A valid query for a non-existent id returns 200 with a null field and no errors")
    @Description("Answers the brief's question: for a missing entity Hygraph returns data "
            + "with the field set to null, not an errors array.")
    void unknownIdYieldsNullFieldNotError() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.fromFile(
                "movie-by-id.graphql", Map.of("id", "this-id-does-not-exist")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.node("/data/movie").isNull())
                .as("the field is present and explicitly null")
                .isTrue();
        assertThat(response.node("/errors").isMissingNode())
                .as("a missing entity is not an error: no errors key at all")
                .isTrue();
    }

    @ApiTest
    @Story("Execution-phase errors")
    @DisplayName("A mutation on a missing record returns 200 with a null field and a field error")
    @Description("The partial-result shape, and the one case where HTTP 200 arrives together "
            + "with an errors array: the request executed, and the error is scoped to a field.")
    void mutationOnMissingRecordIsAFieldLevelError() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.of(
                DELETE_MOVIE, Map.of("id", "this-id-does-not-exist")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.node("/data/deleteMovie").isNull())
                .as("data is present, with the failed field set to null")
                .isTrue();

        GraphQlError error = response.errors().get(0);
        assertThat(error.getMessage()).isEqualTo("not allowed");
        assertThat(error.getExtensions())
                .containsEntry("code", "403")
                // Hygraph nests the path under extensions; see the finding below.
                .containsEntry("path", List.of("deleteMovie"));
    }

    @ApiTest
    @Tag("finding")
    @Story("Specification conformance")
    @DisplayName("FINDING: a field error must carry 'path' as a top-level key, not inside extensions")
    @Description("The GraphQL specification (Response > Errors) requires an error associated with "
            + "a field to contain a top-level 'path' entry. Hygraph places it under 'extensions', "
            + "so spec-following clients cannot attach the error to the field that failed.")
    void fieldErrorMustCarryTopLevelPath() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.of(
                DELETE_MOVIE, Map.of("id", "this-id-does-not-exist")));

        GraphQlError error = response.errors().get(0);
        assertThat(error.getPath())
                .as("field error for deleteMovie must have top-level path [\"deleteMovie\"]; "
                        + "Hygraph returned top-level path=%s and extensions=%s",
                        error.getPath(), error.getExtensions())
                .containsExactly("deleteMovie");
    }

    // ---------------------------------------------------------------
    // Authorization: the permission check fails the whole request
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Authorization errors")
    @DisplayName("A mutation on an existing record is rejected with 403 and changes nothing")
    @Description("The public content API is read-only. The check is not only that the delete "
            + "is refused, but that the record is still there afterwards.")
    void mutationOnExistingRecordIsForbiddenAndHasNoEffect() {
        String id = graphQl.execute(GraphQlRequest.fromFile(
                        "movies-page.graphql", Map.of("first", 1, "skip", 0)))
                .getList("/data/movies", Movie.class).get(0).getId();

        GraphQlResponse response = graphQl.execute(GraphQlRequest.of(DELETE_MOVIE, Map.of("id", id)));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.node("/data").isNull()).isTrue();
        assertThat(response.errors())
                .singleElement()
                .extracting(GraphQlError::getMessage)
                .isEqualTo("Mutation failed due to permission errors");
        assertThat(response.node("/errors/0/extensions/failedActions/0/action").asText())
                .isEqualTo("delete");
        assertThat(response.node("/errors/0/extensions/failedActions/0/model").asText())
                .isEqualTo("Movie");

        // The refusal must be real, not only reported.
        Movie stillThere = graphQl.execute(GraphQlRequest.fromFile(
                "movie-by-id.graphql", Map.of("id", id))).get("/data/movie", Movie.class);
        assertThat(stillThere).as("movie %s survives the refused delete", id).isNotNull();
    }

    // ---------------------------------------------------------------
    // Request phase: the request never runs, so the status is 400
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Request-phase errors")
    @DisplayName("A syntax error returns 400 with a parse error and no data")
    void malformedQueryIsRejectedAtParse() {
        // Unbalanced braces: the document ends mid-selection.
        GraphQlResponse response = graphQl.execute(GraphQlRequest.of("query { movies { id title "));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.errors())
                .singleElement()
                .extracting(GraphQlError::getMessage)
                .asString()
                .contains("ParseError")
                .contains("Unexpected end of input");
        assertThat(response.node("/data").isNull())
                .as("nothing executed, so data is null")
                .isTrue();
    }

    @ApiTest
    @Story("Request-phase errors")
    @DisplayName("Requesting an undefined field returns 400 naming the field and its type")
    void unknownFieldIsRejectedAtValidation() {
        // Syntactically valid, but Movie has no such field.
        GraphQlResponse response = graphQl.execute(GraphQlRequest.of(
                "query { movies(first: 1) { id notARealField } }"));

        assertThat(response.statusCode()).isEqualTo(400);
        // Hygraph reformats the document before validating, so the reported line
        // number does not match the submitted text. Assert on names only.
        assertThat(response.errors())
                .singleElement()
                .extracting(GraphQlError::getMessage)
                .asString()
                .contains("'notARealField'")
                .contains("'Movie'");
        assertThat(response.node("/data").isNull()).isTrue();
    }

    static Stream<Arguments> invalidVariables() {
        Map<String, Object> missingFirst = new HashMap<>();
        missingFirst.put("skip", 0);
        return Stream.of(
                arguments("wrong type: String where Int! is declared",
                        Map.of("first", "two", "skip", 0),
                        "expected type 'Int' for variable 'first'"),
                arguments("required variable omitted",
                        missingFirst,
                        "variable 'first' must be defined"),
                arguments("argument constraint violated: negative page size",
                        Map.of("first", -1, "skip", 0),
                        "must be non negative"));
    }

    @Tag("api")
    @Story("Request-phase errors")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidVariables")
    void invalidVariablesAreRejectedAtValidation(String scenario,
                                                 Map<String, Object> variables,
                                                 String expectedMessage) {
        // The production pagination query, so this proves the real document
        // validates its inputs rather than a throwaway one.
        GraphQlResponse response = graphQl.execute(
                GraphQlRequest.fromFile("movies-page.graphql", variables));

        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(response.errors())
                .as(scenario)
                .singleElement()
                .extracting(GraphQlError::getMessage)
                .asString()
                .contains(expectedMessage);
        assertThat(response.node("/data").isNull()).as(scenario).isTrue();
    }
}
