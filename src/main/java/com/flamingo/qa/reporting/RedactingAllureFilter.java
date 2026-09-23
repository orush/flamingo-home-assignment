package com.flamingo.qa.reporting;

import io.qameta.allure.attachment.DefaultAttachmentProcessor;
import io.qameta.allure.attachment.FreemarkerAttachmentRenderer;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.qameta.allure.attachment.http.HttpResponseAttachment;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.internal.NameAndValue;
import io.restassured.internal.support.Prettifier;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allure's REST Assured filter, with credentials masked.
 *
 * <p>The stock {@code AllureRestAssured} filter writes request bodies and cookies
 * to the report verbatim, which puts the auth password and every session token
 * into a CI artifact. It offers no redaction hook, so this filter mirrors its
 * behaviour and routes headers, cookies and bodies through {@link Redactor}. It
 * reuses Allure's own templates, so the report looks identical.
 */
public class RedactingAllureFilter implements OrderedFilter {

    private static final String REQUEST_TEMPLATE = "http-request.ftl";
    private static final String RESPONSE_TEMPLATE = "http-response.ftl";

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext filterContext) {
        Prettifier prettifier = new Prettifier();

        HttpRequestAttachment.Builder request = HttpRequestAttachment.Builder
                .create("Request", requestSpec.getURI())
                .setMethod(requestSpec.getMethod())
                .setHeaders(redacted(requestSpec.getHeaders()))
                .setCookies(redacted(requestSpec.getCookies()));
        if (requestSpec.getBody() != null) {
            request.setBody(Redactor.redactJson(prettifier.getPrettifiedBodyIfPossible(requestSpec)));
        }
        new DefaultAttachmentProcessor().addAttachment(
                request.build(), new FreemarkerAttachmentRenderer(REQUEST_TEMPLATE));

        Response response = filterContext.next(requestSpec, responseSpec);

        HttpResponseAttachment responseAttachment = HttpResponseAttachment.Builder
                .create(response.getStatusLine())
                .setResponseCode(response.getStatusCode())
                .setHeaders(redacted(response.getHeaders()))
                .setBody(Redactor.redactJson(
                        prettifier.getPrettifiedBodyIfPossible(response, response.getBody())))
                .build();
        new DefaultAttachmentProcessor().addAttachment(
                responseAttachment, new FreemarkerAttachmentRenderer(RESPONSE_TEMPLATE));

        return response;
    }

    private static Map<String, String> redacted(Iterable<? extends NameAndValue> items) {
        Map<String, String> result = new LinkedHashMap<>();
        items.forEach(item -> result.put(item.getName(),
                Redactor.isSensitive(item.getName()) ? Redactor.MASK : item.getValue()));
        return result;
    }

    /** Same position as the stock filter: last, so it sees the final request. */
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
