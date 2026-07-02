package com.app.billing.service;

import com.app.billing.dto.PincodeLookupResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches India pincode data from api.postalpincode.in.
 * Uses a host-scoped lenient SSL client when the JVM trust store rejects the API certificate chain.
 */
@Slf4j
@Service
public class PincodeLookupService {

    static final String PINCODE_API_HOST = "api.postalpincode.in";
    /** Returned when srini Zscaler (or similar) blocks outbound access to the pincode API. */
    public static final String MSG_PROXY_BLOCKED = "PINCODE_PROXY_BLOCKED";
    private static final String USER_MESSAGE =
            "Pincode details could not be fetched. Please enter manually.";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_ATTEMPTS = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate standardRestTemplate = buildRestTemplate(null, null);
    private final RestTemplate lenientRestTemplate = buildLenientRestTemplate();
    private final String pincodeApiBaseUrl;

    public PincodeLookupService(
            @Value("${app.pincode.lookup-base-url:https://api.postalpincode.in/pincode/}") String pincodeApiBaseUrl) {
        String base = pincodeApiBaseUrl == null ? "" : pincodeApiBaseUrl.trim();
        if (base.isEmpty()) {
            base = "https://api.postalpincode.in/pincode/";
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        this.pincodeApiBaseUrl = base;
    }

    public PincodeLookupResponseDto lookup(String pincode) {
        if (pincode == null) {
            return PincodeLookupResponseDto.builder()
                    .success(false)
                    .message("Pincode is required")
                    .areas(new ArrayList<>())
                    .build();
        }
        String trimmed = pincode.trim();
        if (trimmed.isEmpty()) {
            return PincodeLookupResponseDto.builder()
                    .success(false)
                    .message("Pincode is empty")
                    .areas(new ArrayList<>())
                    .build();
        }
        if (!trimmed.matches("\\d{6}")) {
            return PincodeLookupResponseDto.builder()
                    .success(false)
                    .message("Enter a valid 6-digit pincode")
                    .areas(new ArrayList<>())
                    .build();
        }

        String url = pincodeApiBaseUrl + trimmed;
        Exception lastFailure = null;

        for (RestTemplate client : List.of(standardRestTemplate, lenientRestTemplate)) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    PincodeLookupResponseDto parsed = fetchAndParse(client, url);
                    if (MSG_PROXY_BLOCKED.equals(parsed.getMessage())) {
                        return parsed;
                    }
                    if (parsed.isSuccess()) {
                        return parsed;
                    }
                    // API responded: invalid / unknown pincode — do not retry other clients
                    return parsed;
                } catch (Exception e) {
                    lastFailure = e;
                    PincodeLookupResponseDto proxy = tryProxyBlockedFromException(e);
                    if (proxy != null) {
                        log.warn(
                                "Pincode lookup blocked by corporate proxy for {} — use browser lookup or ask InfoSec to allow {}",
                                trimmed,
                                PINCODE_API_HOST);
                        return proxy;
                    }
                    if (!isRetryable(e) || attempt >= MAX_ATTEMPTS) {
                        break;
                    }
                    log.debug("Pincode lookup attempt {} failed for {}", attempt, trimmed);
                }
            }
        }

        logPincodeFailure(trimmed, url, lastFailure);
        return fail(USER_MESSAGE);
    }

    private PincodeLookupResponseDto fetchAndParse(RestTemplate client, String url) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; MyBill/1.0)");
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response;
        try {
            response = client.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (RestClientException ex) {
            PincodeLookupResponseDto proxy = tryProxyBlockedFromException(ex);
            if (proxy != null) {
                return proxy;
            }
            if (isRetryable(ex)) {
                throw ex;
            }
            log.debug("Pincode HTTP client error for {}: {}", url, summarizeError(ex));
            return fail(USER_MESSAGE);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            if (response.getStatusCode().value() == 403 && isZscalerBlockBody(response.getBody())) {
                return proxyBlocked();
            }
            throw new ResourceAccessException("Pincode service returned " + response.getStatusCode());
        }
        String body = response.getBody().trim();
        if (isZscalerBlockBody(body)) {
            return proxyBlocked();
        }
        if (body.isEmpty() || (body.charAt(0) != '[' && body.charAt(0) != '{')) {
            throw new ResourceAccessException("Pincode service returned non-JSON payload");
        }
        return parseApiBody(body);
    }

    private PincodeLookupResponseDto parseApiBody(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray() || root.isEmpty()) {
            return fail(USER_MESSAGE);
        }
        JsonNode first = root.get(0);
        String status = first.path("Status").asText("");
        if (!"Success".equalsIgnoreCase(status)) {
            return PincodeLookupResponseDto.builder()
                    .success(false)
                    .message(first.path("Message").asText("No data for this pincode"))
                    .areas(new ArrayList<>())
                    .build();
        }
        List<PincodeLookupResponseDto.PincodeAreaDto> areas = new ArrayList<>();
        JsonNode offices = first.path("PostOffice");
        if (offices.isArray()) {
            for (JsonNode o : offices) {
                areas.add(PincodeLookupResponseDto.PincodeAreaDto.builder()
                        .name(o.path("Name").asText(""))
                        .district(o.path("District").asText(""))
                        .region(o.path("Region").asText(""))
                        .state(o.path("State").asText(""))
                        .build());
            }
        }
        if (areas.isEmpty()) {
            return PincodeLookupResponseDto.builder()
                    .success(false)
                    .message("No data for this pincode")
                    .areas(new ArrayList<>())
                    .build();
        }
        return PincodeLookupResponseDto.builder()
                .success(true)
                .message("OK")
                .areas(areas)
                .build();
    }

    private static RestTemplate buildLenientRestTemplate() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { TRUST_PINCODE_API }, new SecureRandom());
            HostnameVerifier hostVerifier = (hostname, session) ->
                    PINCODE_API_HOST.equalsIgnoreCase(hostname);
            return buildRestTemplate(sslContext, hostVerifier);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize pincode SSL client", e);
        }
    }

    private static RestTemplate buildRestTemplate(SSLContext sslContext, HostnameVerifier hostVerifier) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection https && sslContext != null && hostVerifier != null) {
                    https.setSSLSocketFactory(sslContext.getSocketFactory());
                    https.setHostnameVerifier(hostVerifier);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** Trust only the external pincode API host when the default JVM trust store rejects its certificate. */
    private static final X509TrustManager TRUST_PINCODE_API = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static PincodeLookupResponseDto tryProxyBlockedFromException(Throwable e) {
        if (e instanceof HttpClientErrorException httpEx
                && httpEx.getStatusCode().value() == 403
                && isZscalerBlockBody(httpEx.getResponseBodyAsString())) {
            return proxyBlocked();
        }
        return null;
    }

    private static boolean isZscalerBlockBody(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("zscaler")
                || lower.contains("srini")
                || lower.contains("internet security by zscaler")
                || lower.contains("website blocked");
    }

    private static PincodeLookupResponseDto proxyBlocked() {
        return PincodeLookupResponseDto.builder()
                .success(false)
                .message(MSG_PROXY_BLOCKED)
                .areas(new ArrayList<>())
                .build();
    }

    private static String summarizeError(Throwable e) {
        String msg = e.toString();
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }

    private static boolean isRetryable(Throwable e) {
        if (e instanceof HttpClientErrorException) {
            return false;
        }
        Throwable t = e;
        while (t != null) {
            if (t instanceof ResourceAccessException
                    || t instanceof SSLHandshakeException
                    || t instanceof ConnectException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void logPincodeFailure(String pincode, String url, Exception e) {
        if (e == null) {
            log.warn("Pincode lookup failed for {} via {}", pincode, url);
            return;
        }
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof SSLHandshakeException) {
            log.warn("Pincode lookup SSL failure for {} via {}: {}", pincode, url, root.toString());
        } else if (e instanceof ResourceAccessException) {
            log.warn("Pincode lookup network failure for {} via {}: {}", pincode, url, root.toString());
        } else {
            log.warn("Pincode lookup failed for {} via {}: {}", pincode, url, e.toString());
        }
    }

    private static PincodeLookupResponseDto fail(String msg) {
        return PincodeLookupResponseDto.builder()
                .success(false)
                .message(msg)
                .areas(new ArrayList<>())
                .build();
    }
}
