package com.cabservice.worker.email;

import com.cabservice.messaging.OtpEmailMessage;
import com.cabservice.worker.config.WorkerRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Receives "send this OTP" jobs from RabbitMQ and sends the email.
 * If sending throws, the message is dropped (see default-requeue-rejected in application.yml)
 * and the user can request a new OTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpEmailListener {

    private final JavaMailSender mailSender;

    @Value("${cabservice.mail.from}")
    private String from;

    @RabbitListener(queues = WorkerRabbitConfig.OTP_EMAIL_QUEUE)
    public void handle(OtpEmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.email());
        mail.setSubject("Your CabService verification code");
        mail.setText("Hi " + message.fullName() + ",\n\n"
                + "Your verification code is: " + message.otp() + "\n\n"
                + "It expires in " + message.expiresInMinutes() + " minutes. "
                + "If you did not try to register, you can ignore this email.\n\n"
                + "CabService");

        mailSender.send(mail);
        log.info("OTP email sent to {}", mask(message.email())); // never log the OTP itself
    }

    private static String mask(String email) {
        return email.replaceAll("(^.).*(@.*$)", "$1***$2");
    }
}