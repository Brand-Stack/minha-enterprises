package com.app.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

@Slf4j
@Service
public class HikvisionIsapiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final SSLSocketFactory TRUST_ALL_SSL_FACTORY;
    private static final HostnameVerifier TRUST_ALL_HOSTNAME_VERIFIER = (hostname, session) -> true;

    static {
        SSLSocketFactory factory = null;
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509CertificateTrustManager()
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            factory = sslContext.getSocketFactory();
        } catch (Exception e) {
            log.error("Failed to initialize TrustAll SSLContext for ISAPI", e);
        }
        TRUST_ALL_SSL_FACTORY = factory;
    }

    private static class X509CertificateTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }

    public record ConnectionTestResult(
            boolean success,
            String status,
            String message,
            String model,
            String serialNumber,
            String firmwareVersion
    ) {}

    public ConnectionTestResult testConnection(String ip, int port, boolean useHttps, String username, String password) {
        String baseUrl = (useHttps ? "https://" : "http://") + ip + ":" + port;
        String endpoint = baseUrl + "/ISAPI/System/deviceInfo";

        try {
            ApiResponse response = executeRequestWithDigest("GET", endpoint, null, username, password, useHttps);
            if (response.statusCode == 200) {
                String xml = response.body;
                String model = extractXmlTag(xml, "model");
                String serial = extractXmlTag(xml, "serialNumber");
                String firmware = extractXmlTag(xml, "firmwareVersion");
                return new ConnectionTestResult(true, "CONNECTED", "Device connected successfully", model, serial, firmware);
            } else if (response.statusCode == 401) {
                return new ConnectionTestResult(false, "AUTH_FAILED", "Authentication failed: Invalid device username or password", null, null, null);
            } else {
                return new ConnectionTestResult(false, "ERROR", "Device returned HTTP status: " + response.statusCode, null, null, null);
            }
        } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
            return new ConnectionTestResult(false, "UNREACHABLE", "Device unreachable or connection timed out: " + e.getMessage(), null, null, null);
        } catch (Exception e) {
            return new ConnectionTestResult(false, "ERROR", "Connection error: " + e.getMessage(), null, null, null);
        }
    }

    public record HikvisionPunchEvent(
            long serialNo,
            String employeeNo,
            String timestamp,
            int major,
            int minor,
            String verifyMode,
            String rawJson
    ) {}

    public List<HikvisionPunchEvent> fetchAccessEvents(String ip, int port, boolean useHttps, String username, String password, Long startSerialNo, int maxResults) {
        String baseUrl = (useHttps ? "https://" : "http://") + ip + ":" + port;
        String endpoint = baseUrl + "/ISAPI/AccessControl/AcsEvent?format=json";

        List<HikvisionPunchEvent> events = new ArrayList<>();
        try {
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("searchID", UUID.randomUUID().toString().substring(0, 8));
            cond.put("searchResultPosition", 0);
            cond.put("maxResults", Math.min(maxResults, 30));
            cond.put("major", 5); // 5 = Access Control Events
            cond.put("minor", 0); // 0 = all access events (including 38/75 fingerprint/card/face valid auth)
            if (startSerialNo != null && startSerialNo > 0) {
                cond.put("beginSerialNo", startSerialNo + 1);
                cond.put("endSerialNo", 2147483647L);
            }

            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("AcsEventCond", cond);
            String jsonBody = objectMapper.writeValueAsString(requestPayload);

            ApiResponse response = executeRequestWithDigest("POST", endpoint, jsonBody, username, password, useHttps);
            if (response.statusCode == 200) {
                JsonNode root = objectMapper.readTree(response.body);
                JsonNode acsEventNode = root.path("AcsEvent");
                JsonNode infoList = acsEventNode.path("InfoList");
                if (infoList.isArray()) {
                    for (JsonNode item : infoList) {
                        long serialNo = item.path("serialNo").asLong(0);
                        String employeeNo = item.has("employeeNoString") ? item.path("employeeNoString").asText() :
                                            (item.has("employeeNo") ? item.path("employeeNo").asText() : "");
                        String time = item.path("time").asText();
                        int major = item.path("major").asInt();
                        int minor = item.path("minor").asInt();
                        String verifyMode = item.path("currentVerifyMode").asText("");

                        // Filter only valid verification events (e.g. minor 38, 75, or with employeeNo present)
                        if (employeeNo != null && !employeeNo.isBlank()) {
                            events.add(new HikvisionPunchEvent(serialNo, employeeNo, time, major, minor, verifyMode, item.toString()));
                        }
                    }
                }
            } else {
                log.warn("Failed to fetch AcsEvent from Hikvision device {}: HTTP {}", ip, response.statusCode);
            }
        } catch (Exception e) {
            log.error("Error fetching access events from Hikvision device {}: {}", ip, e.getMessage(), e);
        }
        return events;
    }

    private static class ApiResponse {
        int statusCode;
        String body;
        Map<String, List<String>> headers;

        ApiResponse(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }
    }

    private ApiResponse executeRequestWithDigest(String method, String urlString, String postData, String username, String password, boolean useHttps) throws Exception {
        // First try unauthenticated to receive 401 WWW-Authenticate challenge
        ApiResponse initialResponse = sendHttpRequest(method, urlString, postData, null, useHttps);
        if (initialResponse.statusCode != 401) {
            return initialResponse;
        }

        List<String> authHeaders = initialResponse.headers.get("WWW-Authenticate");
        if (authHeaders == null || authHeaders.isEmpty()) {
            authHeaders = initialResponse.headers.get("www-authenticate");
        }
        if (authHeaders == null || authHeaders.isEmpty()) {
            return initialResponse;
        }

        String digestChallenge = null;
        for (String h : authHeaders) {
            if (h.toLowerCase().startsWith("digest")) {
                digestChallenge = h;
                break;
            }
        }
        if (digestChallenge == null) {
            return initialResponse;
        }

        URI uri = URI.create(urlString);
        String uriPath = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");

        String authorizationHeader = calculateDigestAuthHeader(digestChallenge, method, uriPath, username, password);
        return sendHttpRequest(method, urlString, postData, authorizationHeader, useHttps);
    }

    private ApiResponse sendHttpRequest(String method, String urlString, String postData, String authHeader, boolean useHttps) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (useHttps && conn instanceof HttpsURLConnection httpsConn) {
            if (TRUST_ALL_SSL_FACTORY != null) {
                httpsConn.setSSLSocketFactory(TRUST_ALL_SSL_FACTORY);
            }
            httpsConn.setHostnameVerifier(TRUST_ALL_HOSTNAME_VERIFIER);
        }

        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(false);

        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        if (postData != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
            }
        }

        int statusCode = conn.getResponseCode();
        InputStream is = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = "";
        if (is != null) {
            try (Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                responseBody = s.hasNext() ? s.next() : "";
            }
        }
        return new ApiResponse(statusCode, responseBody, conn.getHeaderFields());
    }

    private String calculateDigestAuthHeader(String challenge, String method, String uri, String username, String password) throws NoSuchAlgorithmException {
        Map<String, String> params = parseDigestChallenge(challenge);
        String realm = params.getOrDefault("realm", "");
        String nonce = params.getOrDefault("nonce", "");
        String qop = params.getOrDefault("qop", "");
        String opaque = params.getOrDefault("opaque", "");

        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + uri);

        String nc = "00000001";
        String cnonce = UUID.randomUUID().toString().substring(0, 8);

        String response;
        if (qop != null && !qop.isBlank() && qop.contains("auth")) {
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
            StringBuilder sb = new StringBuilder("Digest ");
            sb.append("username=\"").append(username).append("\", ");
            sb.append("realm=\"").append(realm).append("\", ");
            sb.append("nonce=\"").append(nonce).append("\", ");
            sb.append("uri=\"").append(uri).append("\", ");
            sb.append("qop=auth, ");
            sb.append("nc=").append(nc).append(", ");
            sb.append("cnonce=\"").append(cnonce).append("\", ");
            sb.append("response=\"").append(response).append("\"");
            if (opaque != null && !opaque.isBlank()) {
                sb.append(", opaque=\"").append(opaque).append("\"");
            }
            return sb.toString();
        } else {
            response = md5(ha1 + ":" + nonce + ":" + ha2);
            StringBuilder sb = new StringBuilder("Digest ");
            sb.append("username=\"").append(username).append("\", ");
            sb.append("realm=\"").append(realm).append("\", ");
            sb.append("nonce=\"").append(nonce).append("\", ");
            sb.append("uri=\"").append(uri).append("\", ");
            sb.append("response=\"").append(response).append("\"");
            if (opaque != null && !opaque.isBlank()) {
                sb.append(", opaque=\"").append(opaque).append("\"");
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseDigestChallenge(String header) {
        Map<String, String> map = new HashMap<>();
        String clean = header.substring(6).trim(); // remove "Digest "
        String[] pairs = clean.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim().replace("\"", "");
                map.put(key, val);
            }
        }
        return map;
    }

    private String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String extractXmlTag(String xml, String tag) {
        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int start = xml.indexOf(openTag);
        if (start != -1) {
            int end = xml.indexOf(closeTag, start);
            if (end != -1) {
                return xml.substring(start + openTag.length(), end).trim();
            }
        }
        return null;
    }
}
