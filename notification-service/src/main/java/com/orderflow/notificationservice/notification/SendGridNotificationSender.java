package com.orderflow.notificationservice.notification;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Real SendGrid email delivery, active when {@code orderflow.notification.provider=sendgrid}
 * (env: {@code NOTIFICATION_PROVIDER=sendgrid}) with {@code SENDGRID_API_KEY} set to a SendGrid
 * API key with Mail Send permission.
 *
 * <p><b>Unverified against a live SendGrid account</b> - written against the documented
 * {@code sendgrid-java} 4.10.x {@code mail/send} API (the same "hello email" shape SendGrid's own
 * quickstart uses) but never exercised end-to-end since no API key was available while building
 * this. Smoke-test against your own SendGrid account before relying on it. In particular,
 * SendGrid requires the {@code from} address to be a verified sender/domain on your account - an
 * unverified {@code orderflow.notification.sendgrid.from-address} will get every send rejected.
 */
@Component
@ConditionalOnProperty(prefix = "orderflow.notification", name = "provider", havingValue = "sendgrid")
public class SendGridNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridNotificationSender.class);

    private final SendGrid sendGrid;
    private final String fromAddress;

    public SendGridNotificationSender(
            @Value("${orderflow.notification.sendgrid.api-key:}") String apiKey,
            @Value("${orderflow.notification.sendgrid.from-address:orders@orderflow.local}") String fromAddress) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("orderflow.notification.provider=sendgrid but SENDGRID_API_KEY is blank - " +
                    "every send will fail until it's set.");
        }
        this.sendGrid = new SendGrid(apiKey);
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipientPlaceholder, String subject, String body) {
        Mail mail = new Mail(new Email(fromAddress), subject, new Email(recipientPlaceholder),
                new Content("text/plain", body));

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid send failed: status={} body={}", response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("SendGrid send threw IOException", e);
        }
    }
}
