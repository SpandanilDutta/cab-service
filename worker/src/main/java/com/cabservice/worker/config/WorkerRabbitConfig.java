package com.cabservice.worker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ setup for the worker (consumer side).
 * The names MUST be identical to the API's RabbitConfig. Declaring the same
 * exchange/queue in both apps means it does not matter which one starts first.
 */
@Configuration
public class WorkerRabbitConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "cab.notifications";
    public static final String OTP_EMAIL_QUEUE = "cab.email.otp";
    public static final String OTP_EMAIL_ROUTING_KEY = "email.otp";

    @Bean
    public DirectExchange notificationsExchange() {
        return new DirectExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue otpEmailQueue() {
        return QueueBuilder.durable(OTP_EMAIL_QUEUE).build();
    }

    @Bean
    public Binding otpEmailBinding(Queue otpEmailQueue, DirectExchange notificationsExchange) {
        return BindingBuilder.bind(otpEmailQueue).to(notificationsExchange).with(OTP_EMAIL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.cabservice.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}