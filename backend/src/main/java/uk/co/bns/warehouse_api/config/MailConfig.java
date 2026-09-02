package uk.co.bns.warehouse_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${mail.smtp-host}")
    private String host;

    @Value("${mail.smtp-port}")
    private int port;

    @Value("${mail.smtp-username}")
    private String username;

    @Value("${mail.smtp-password}")
    private String password;

    /**
     * Only registers a JavaMailSender bean if SMTP_HOST has actually been set.
     * Returning null here (rather than always building a sender pointed at nothing)
     * means AcknowledgementService can check for its absence and be upfront about
     * emails not really being sent, instead of failing confusingly at send time.
     */
    @Bean
    public JavaMailSender javaMailSender() {
        if (host == null || host.isBlank()) {
            return null;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
