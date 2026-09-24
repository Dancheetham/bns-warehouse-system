package uk.co.bns.warehouse_api.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

/**
 * Builds a mail sender fresh from current Settings on every send, rather
 * than once at application startup from static .env-only properties (the
 * previous MailConfig/JavaMailSender bean approach) - so changing SMTP
 * settings from Settings > Email takes effect on the very next email sent,
 * no restart needed. Falls back to whatever's in .env as the *default* for
 * each setting, so anyone with SMTP already configured there keeps working
 * unchanged until they explicitly configure it through the UI instead.
 *
 * When a triggering user is given, their own per-user email settings
 * (Settings > My Account, reusing the same per-user store as status colour
 * customisation) override username/password/from-address/cc for that one
 * send - so an acknowledgement genuinely goes out as the person who sent it,
 * not a single shared mailbox. Host/port stay shared/global deliberately -
 * most mail providers (e.g. Office 365) use the same host for every mailbox
 * in an organisation, only the mailbox credentials themselves differ.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SettingsService settingsService;
    private final UserSettingsService userSettingsService;

    @Value("${mail.smtp-host:}")
    private String envHost;

    @Value("${mail.smtp-port:587}")
    private String envPort;

    @Value("${mail.smtp-username:}")
    private String envUsername;

    @Value("${mail.smtp-password:}")
    private String envPassword;

    @Value("${mail.from-address:sales@bnsdistribution.example}")
    private String envFromAddress;

    public record SendResult(boolean sent, String reason) {}

    public boolean isConfigured() {
        return !host().isBlank();
    }

    public SendResult send(String to, String subject, String body) {
        return send(to, subject, body, null);
    }

    public SendResult send(String to, String subject, String body, String triggeringUserName) {
        String host = host();
        if (host.isBlank()) {
            return new SendResult(false,
                    "SMTP is not configured (Settings > Email) - email was not actually sent, but here's what would have gone out");
        }

        Map<String, String> userOverrides = userOverridesFor(triggeringUserName);

        try {
            JavaMailSenderImpl sender = buildSender(host, userOverrides);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(fromAddress(userOverrides));
            String cc = userOverrides.get("email_cc_address");
            if (cc != null && !cc.isBlank()) {
                message.setCc(cc);
            }
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);

            return new SendResult(true, "Sent");
        } catch (Exception e) {
            return new SendResult(false, "Failed to send: " + e.getMessage());
        }
    }

    /**
     * Same as send(), plus one file attachment - used by InvoiceService to
     * send the generated invoice/credit note PDF. Always goes out through
     * the shared account (no triggeringUserName) since Dan asked for
     * invoices to come "from the shared email address set on the system",
     * not whoever happened to click Generate.
     */
    public SendResult sendWithAttachment(String to, String subject, String body,
                                          String attachmentFilename, byte[] attachmentBytes, String attachmentContentType) {
        String host = host();
        if (host.isBlank()) {
            return new SendResult(false,
                    "SMTP is not configured (Settings > Email) - email was not actually sent");
        }

        try {
            JavaMailSenderImpl sender = buildSender(host, Map.of());
            MimeMessage mimeMessage = sender.createMimeMessage();
            // multipart=true is what actually enables attachments - a plain
            // MimeMessageHelper defaults to a single-part message otherwise.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setFrom(fromAddress(Map.of()));
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes), attachmentContentType);
            sender.send(mimeMessage);

            return new SendResult(true, "Sent");
        } catch (Exception e) {
            return new SendResult(false, "Failed to send: " + e.getMessage());
        }
    }

    private JavaMailSenderImpl buildSender(String host, Map<String, String> userOverrides) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(Integer.parseInt(settingsService.get("smtp_port", envPort)));
        sender.setUsername(overrideOr(userOverrides, "email_username", settingsService.get("smtp_username", envUsername)));
        sender.setPassword(overrideOr(userOverrides, "email_password", settingsService.get("smtp_password", envPassword)));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }

    private Map<String, String> userOverridesFor(String triggeringUserName) {
        return (triggeringUserName == null) ? Map.of() : userSettingsService.getForUser(triggeringUserName);
    }

    private String fromAddress(Map<String, String> userOverrides) {
        return overrideOr(userOverrides, "email_from_address", settingsService.get("mail_from_address", envFromAddress));
    }

    private String overrideOr(Map<String, String> userOverrides, String key, String fallback) {
        String value = userOverrides.get(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String host() {
        return settingsService.get("smtp_host", envHost);
    }
}
