package com.slotmind.service;

import com.slotmind.domain.NotificationChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;

@Component
public class ConfiguredNotificationSender implements NotificationSender {
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final RestClient restClient;
    private final String emailFrom;
    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final String twilioFromNumber;

    public ConfiguredNotificationSender(
            ObjectProvider<JavaMailSender> mailSenders,
            RestClient.Builder restClientBuilder,
            @Value("${slotmind.notifications.email.from:}") String emailFrom,
            @Value("${slotmind.notifications.twilio.account-sid:}") String twilioAccountSid,
            @Value("${slotmind.notifications.twilio.auth-token:}") String twilioAuthToken,
            @Value("${slotmind.notifications.twilio.from-number:}") String twilioFromNumber) {
        this.mailSenders = mailSenders;
        this.restClient = restClientBuilder.build();
        this.emailFrom = emailFrom;
        this.twilioAccountSid = twilioAccountSid;
        this.twilioAuthToken = twilioAuthToken;
        this.twilioFromNumber = twilioFromNumber;
    }

    @Override
    public void send(NotificationChannel channel, String recipient, String message) {
        switch (channel) {
            case EMAIL -> sendEmail(recipient, message);
            case SMS -> sendSms(recipient, message);
            case LOG -> new LoggingNotificationSender().send(channel, recipient, message);
        }
    }

    private void sendEmail(String recipient, String message) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null || emailFrom.isBlank()) {
            throw new IllegalStateException("Email delivery is not configured; set SMTP properties and SLOTMIND_EMAIL_FROM.");
        }
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(emailFrom);
        email.setTo(recipient);
        email.setSubject("SlotMind appointment update");
        email.setText(message);
        sender.send(email);
    }

    private void sendSms(String recipient, String message) {
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank() || twilioFromNumber.isBlank()) {
            throw new IllegalStateException("SMS delivery is not configured; set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", twilioFromNumber);
        form.add("To", recipient);
        form.add("Body", message);
        String encodedSid = UriUtils.encodePathSegment(twilioAccountSid, StandardCharsets.UTF_8);
        restClient.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/" + encodedSid + "/Messages.json")
                .headers(headers -> headers.setBasicAuth(twilioAccountSid, twilioAuthToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
