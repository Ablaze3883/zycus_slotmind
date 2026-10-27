package com.slotmind.service;

import com.slotmind.domain.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class ConfiguredNotificationSenderTest {
    @Test
    void sendsEmailUsingConfiguredSenderAndFromAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ConfiguredNotificationSender sender = sender(mailSender, RestClient.builder(),
                "clinic@example.com", "", "", "");

        sender.send(NotificationChannel.EMAIL, "patient@example.com", "Your visit is confirmed.");

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("clinic@example.com");
        assertThat(message.getValue().getTo()).containsExactly("patient@example.com");
        assertThat(message.getValue().getText()).isEqualTo("Your visit is confirmed.");
    }

    @Test
    void sendsSmsThroughTwilioWithEncodedFormAndBasicAuth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ConfiguredNotificationSender sender = sender(null, builder, "",
                "AC123", "test-token", "+15551234567");
        server.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, startsWith("Basic ")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To=%2B15550102030")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Body=Your+visit+is+confirmed.")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        sender.send(NotificationChannel.SMS, "+15550102030", "Your visit is confirmed.");
        server.verify();
    }

    @Test
    void unconfiguredDeliveryFailsExplicitly() {
        ConfiguredNotificationSender sender = sender(null, RestClient.builder(), "", "", "", "");

        assertThatThrownBy(() -> sender.send(NotificationChannel.EMAIL, "patient@example.com", "Update"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Email delivery is not configured");
        assertThatThrownBy(() -> sender.send(NotificationChannel.SMS, "+15550102030", "Update"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SMS delivery is not configured");
    }

    private static ConfiguredNotificationSender sender(JavaMailSender mailSender, RestClient.Builder builder,
                                                       String emailFrom, String accountSid, String token, String fromNumber) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (mailSender != null) beanFactory.addBean("mailSender", mailSender);
        ObjectProvider<JavaMailSender> provider = beanFactory.getBeanProvider(JavaMailSender.class);
        return new ConfiguredNotificationSender(provider, builder, emailFrom, accountSid, token, fromNumber);
    }
}
