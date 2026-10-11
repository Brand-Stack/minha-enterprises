package com.app.billing.service;

import com.app.billing.dto.OnboardQuotationDto;
import com.app.billing.util.AttachmentFilenameUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardEmailService {

    private final OnboardQuotationService onboardQuotationService;
    private final OnboardQuotationPdfService onboardQuotationPdfService;
    private final EmailDispatchService emailDispatchService;

    public void sendOnboardQuotationEmail(String quotationId, String recipientEmail) {
        OnboardQuotationDto dto = onboardQuotationService.findById(quotationId);
        byte[] pdf = onboardQuotationPdfService.generate(dto);

        String subject = "Onboard Rate Quotation - " + dto.getQuotationNumber() + " - " + dto.getCustomerName();
        String body = "<p>Dear " + dto.getCustomerName() + ",</p>" +
                "<p>Please find attached our official Onboard Courier Rate Quotation (Ref: <strong>" + dto.getQuotationNumber() + "</strong>).</p>" +
                "<p>Thank you for considering our services.</p>";

        String filename = AttachmentFilenameUtil.courierQuotationPdf(dto.getQuotationNumber(), dto.getCustomerName());

        EmailDispatchService.EmailAttachment attachment = new EmailDispatchService.EmailAttachment(
                filename, "application/pdf", pdf);

        List<EmailDispatchService.EmailAttachment> attachments = Collections.singletonList(attachment);

        emailDispatchService.sendWithAttachments(
                recipientEmail,
                subject,
                body,
                true,
                attachments,
                null
        );

        log.info("Sent Onboard Quotation email for {} to {}", dto.getQuotationNumber(), recipientEmail);
    }
}
