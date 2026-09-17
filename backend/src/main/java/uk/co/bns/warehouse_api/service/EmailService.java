package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Builds a mail sender fresh from current Settings on every send, rather
 * than once at application startup from static .env-only properties (the
 * previous MailConfig/JavaMailSender bean approach) - so changing SMTP
 * settings from Settings > Email takes effect on the very next email sent,
 * no restart needed. Falls back to whatever's in .env as the *default* for
 * each setting, so anyone with SMTP already configured there keeps working
 * unchanged until they explicitly configure it through the UI instead.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SettingsService settingsService;

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
        String host = host();
        if (host.isBlank()) {
            return new SendResult(false,
                    "SMTP is not configured (Settings > Email) - email was not actually sent, but here's what would have gone out");
        }

        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(Integer.parseInt(settingsService.get("smtp_port", envPort)));
            sender.setUsername(settingsService.get("smtp_username", envUsername));
            sender.setPassword(settingsService.get("smtp_password", envPassword));

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(settingsService.get("mail_from_address", envFromAddress));
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);

            return new SendResult(true, "Sent");
        } catch (Exception e) {
            return new SendResult(false, "Failed to send: " + e.getMessage());
        }
    }

    private String host() {
        return settingsService.get("smtp_host", envHost);
    }
}
