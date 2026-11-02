package com.cabservice.auth.service;

import com.cabservice.common.exception.ApiException;
import com.cabservice.config.RabbitConfig;
import com.cabservice.messaging.OtpEmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Hands the "send this OTP email" job to RabbitMQ. The worker does the actual sending,
 * so a slow or failing mail server never slows down or breaks the API request.
 *
 * Never log the message: it contains the OTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpEmailPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(OtpEmailMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.NOTIFICATIONS_EXCHANGE,
                    RabbitConfig.OTP_EMAIL_ROUTING_KEY,
                    message);
        } catch (AmqpException e) {
            log.error("Could not publish OTP email job to RabbitMQ", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We could not send the OTP right now. Please try again in a moment.");
        }
    }
}