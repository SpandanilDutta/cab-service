package com.cabservice.config;

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
 * RabbitMQ setup for the API (producer side).
 * The worker declares the same exchange/queue, so it does not matter which app starts first.
 * Keep the names below identical in WorkerRabbitConfig.
 */
@Configuration
public class RabbitConfig {

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

    // Messages travel as JSON. Spring Boot picks this bean up for RabbitTemplate automatically.
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.cabservice.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}