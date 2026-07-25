package com.app.billing.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatchService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    public record EmailAttachment(String filename, String contentType, byte[] content) {
    }

    public record EmailInlineAttachment(String contentId, org.springframework.core.io.Resource resource) {
    }

    /** Send to one or more recipients. Single to address or comma-separated list accepted. */
    public void sendWithAttachments(String to,
                                    String subject,
                                    String body,
                                    boolean isHtml,
                                    List<EmailAttachment> attachments,
                                    List<EmailInlineAttachment> inline) {
        String[] addresses = to != null && !to.isBlank()
                ? java.util.Arrays.stream(to.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toArray(String[]::new)
                : new String[0];
        if (addresses.length == 0) {
            throw new IllegalArgumentException("At least one recipient email is required.");
        }
        sendWithAttachments(addresses, subject, body, isHtml, attachments, inline);
    }

    /** Send to multiple recipients. All receive the same email and attachments. */
    public void sendWithAttachments(String[] toAddresses,
                                    String subject,
                                    String body,
                                    boolean isHtml,
                                    List<EmailAttachment> attachments,
                                    List<EmailInlineAttachment> inline) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            if (defaultFrom != null && !defaultFrom.isBlank()) {
                helper.setFrom(defaultFrom);
            }
            helper.setTo(toAddresses);
            helper.setSubject(subject);
            helper.setText(body, isHtml);

            if (attachments != null) {
                for (EmailAttachment attachment : attachments) {
                    if (attachment == null || attachment.content() == null || attachment.content().length == 0) {
                        continue;
                    }
                    helper.addAttachment(
                            attachment.filename(),
                            new ByteArrayResource(attachment.content()),
                            attachment.contentType()
                    );
                }
            }
            if (inline != null) {
                for (EmailInlineAttachment ia : inline) {
                    if (ia != null && ia.contentId() != null && ia.resource() != null) {
                        try {
                            helper.addInline(ia.contentId(), ia.resource());
                        } catch (Exception e) {
                            log.warn("Failed to attach inline resource {}: {}", ia.contentId(), e.getMessage());
                        }
                    }
                }
            }

            mailSender.send(mimeMessage);
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP Authentication failed: {}", e.getMessage(), e);
            throw new RuntimeException("SMTP authentication failed.", e);
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Failed to send email due to MailSendException", e);
            Throwable rootCause = e.getMostSpecificCause();
            String rootMessage = rootCause != null ? rootCause.getMessage() : "";

            if (rootMessage.contains("Authentication") || rootMessage.contains("Username and Password not accepted") || rootMessage.contains("535 5.7.8")) {
                throw new RuntimeException("SMTP authentication failed.", e);
            }

            String host = "smtp.gmail.com";
            int port = 465;
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                host = impl.getHost();
                port = impl.getPort();
            }

            if (rootCause instanceof java.net.ConnectException ||
                rootCause instanceof java.net.SocketException ||
                rootCause instanceof java.net.SocketTimeoutException ||
                rootMessage.contains("Connection timed out") ||
                rootMessage.contains("timed out") ||
                rootMessage.contains("connect") ||
                rootMessage.contains("Connection refused")) {
                
                String diagnosis = diagnoseConnectionFailure(host, port);
                throw new RuntimeException(diagnosis, e);
            }

            if (rootCause instanceof java.net.UnknownHostException ||
                rootMessage.contains("unreachable") ||
                rootMessage.contains("UnknownHost")) {
                throw new RuntimeException("Unable to connect to SMTP server.", e);
            }

            throw new RuntimeException("Email configuration is invalid.", e);
        } catch (Exception e) {
            log.error("Failed to send email to {}", java.util.Arrays.toString(toAddresses), e);
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String msg = cause.getMessage() != null ? cause.getMessage() : "";

            if (msg.contains("Authentication") || msg.contains("Username and Password not accepted") || msg.contains("AuthenticationFailedException")) {
                throw new RuntimeException("SMTP authentication failed.", e);
            }

            String host = "smtp.gmail.com";
            int port = 465;
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                host = impl.getHost();
                port = impl.getPort();
            }

            if (msg.contains("Connection timed out") || msg.contains("ConnectException") || msg.contains("SocketTimeoutException") || msg.contains("Connection refused")) {
                String diagnosis = diagnoseConnectionFailure(host, port);
                throw new RuntimeException(diagnosis, e);
            }
            if (msg.contains("UnknownHostException") || msg.contains("unreachable")) {
                throw new RuntimeException("Unable to connect to SMTP server.", e);
            }
            throw new RuntimeException("Email configuration is invalid.", e);
        }
    }

    private String diagnoseConnectionFailure(String host, int port) {
        log.info("Running SMTP connection diagnostics to {}:{}", host, port);
        
        // 1. DNS Resolution Check
        try {
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                log.error("DNS Resolution returned no IP addresses for host: {}", host);
                return "Unable to connect to SMTP server.";
            }
            log.info("DNS resolved successfully for host: {} -> {}", host, java.util.Arrays.toString(addresses));
        } catch (java.net.UnknownHostException e) {
            log.error("DNS resolution failed for host: {}", host, e);
            return "Unable to connect to SMTP server.";
        }

        // 2. Outbound check to verify general internet connectivity (connect to google.com:443)
        boolean generalInternetWorks = false;
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("google.com", 443), 3000);
            generalInternetWorks = true;
            log.info("General outbound internet connectivity check (google.com:443) succeeded.");
        } catch (Exception e) {
            log.warn("General outbound internet connectivity check (google.com:443) failed: {}", e.getMessage());
        }

        if (generalInternetWorks) {
            log.error("General internet works, but SMTP connection to {}:{} timed out/failed. Outbound SMTP traffic on port {} is blocked by server firewall or cloud provider.", host, port, port);
            return "Server is blocking outbound SMTP traffic.";
        } else {
            log.error("SMTP connection to {}:{} failed, and general internet check also failed. Network is offline or unreachable.", host, port);
            return "SMTP connection timed out.";
        }
    }
}
