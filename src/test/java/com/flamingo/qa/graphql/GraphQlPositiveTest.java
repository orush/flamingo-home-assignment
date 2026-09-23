package com.flamingo.qa.graphql;

import com.flamingo.qa.api.graphql.GraphQlClient;
import com.flamingo.qa.api.graphql.GraphQlRequest;
import com.flamingo.qa.api.graphql.GraphQlResponse;
import com.flamingo.qa.api.graphql.QueryLoader;
import com.flamingo.qa.api.graphql.model.Movie;
import com.flamingo.qa.junit.ApiTest;
import com.flamingo.qa.junit.RetryOnNetworkError;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Epic("Hygraph GraphQL")
@Feature("Queries")
class GraphQlPositiveTest {

    private static final int PAGE_SIZE = 5;

    private final GraphQlClient graphQl = new GraphQlClient();

    private GraphQlResponse moviesPage(int first, int skip) {
        return graphQl.execute(GraphQlRequest.fromFile(
                "movies-page.graphql", Map.of("first", first, "skip", skip)));
    }

    @RetryOnNetworkError
    @Tag("api")
    @DisplayName("A page size limits the list to exactly that many movies")
    void limitsListToRequestedPageSize() {
        GraphQlResponse response = moviesPage(3, 0);

        // Hard: on a failed request the data paths below are absent and throw.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertSoftly(softly -> {
            softly.assertThat(response.getList("/data/movies", Movie.class)).hasSize(3);
            softly.assertThat(response.get("/data/moviesConnection/aggregate/count", Integer.class))
                    .isGreaterThanOrEqualTo(3);
        });
    }

    @ApiTest
    @DisplayName("Paging through the whole collection visits every movie exactly once")
    void pagingVisitsEveryMovieExactlyOnce() {
        int total = moviesPage(1, 0).get("/data/moviesConnection/aggregate/count", Integer.class);
        assertThat(total)
                .as("collection must be small enough to walk politely on a public service")
                .isBetween(1, 100);

        List<String> seen = new ArrayList<>();
        for (int skip = 0; skip < total; skip += PAGE_SIZE) {
            List<Movie> page = moviesPage(PAGE_SIZE, skip).getList("/data/movies", Movie.class);
            assertThat(page).as("page starting at skip=%d", skip)
                    .isNotEmpty()
                    .hasSizeLessThanOrEqualTo(PAGE_SIZE);
            page.forEach(movie -> seen.add(movie.getId()));
        }

        // No gaps and no overlaps: together the pages are exactly the collection.
        assertThat(seen).hasSize(total).doesNotHaveDuplicates();
    }

    @ApiTest
    @DisplayName("A single movie can be fetched by its id")
    void fetchesSingleMovieById() {
        Movie expected = moviesPage(1, 0).getList("/data/movies", Movie.class).get(0);

        GraphQlResponse response = graphQl.execute(GraphQlRequest.fromFile(
                "movie-by-id.graphql", Map.of("id", expected.getId())));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.get("/data/movie", Movie.class)).isEqualTo(expected);
    }

    @ApiTest
    @DisplayName("One query document serves different variables: values are never interpolated")
    void variablesDriveResultsWithoutChangingTheQuery() {
        String document = QueryLoader.load("movie-by-id.graphql");
        List<Movie> candidates = moviesPage(2, 0).getList("/data/movies", Movie.class);
        Movie first = candidates.get(0);
        Movie second = candidates.get(1);

        Movie fetchedFirst = graphQl.execute(GraphQlRequest.of(document, Map.of("id", first.getId())))
                .get("/data/movie", Movie.class);
        Movie fetchedSecond = graphQl.execute(GraphQlRequest.of(document, Map.of("id", second.getId())))
                .get("/data/movie", Movie.class);

        assertSoftly(softly -> {
            softly.assertThat(fetchedFirst).isEqualTo(first);
            softly.assertThat(fetchedSecond).isEqualTo(second);
            // The identical document produced both results, and no id was ever
            // spliced into it: the values travelled only as variables.
            softly.assertThat(document)
                    .contains("$id")
                    .doesNotContain(first.getId())
                    .doesNotContain(second.getId());
        });
    }

    @ApiTest
    @DisplayName("A fragment resolves alongside a nested field from a related type")
    void resolvesFragmentWithNestedPublisher() {
        GraphQlResponse response = graphQl.execute(GraphQlRequest.fromFile(
                "movies-with-publisher.graphql", Map.of("first", 3)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.hasErrors()).isFalse();
        assertThat(response.getList("/data/movies", Movie.class))
                .hasSize(3)
                .allSatisfy(movie -> {
                    assertThat(movie.getId()).isNotBlank();                    // from the fragment
                    assertThat(movie.getTitle()).isNotBlank();                 // from the fragment
                    assertThat(movie.getPublishedBy().getName()).isNotBlank(); // Movie -> User
                });
    }
}
