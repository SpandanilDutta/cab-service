package com.cabservice.security.userdetails;

import com.cabservice.user.entity.User;
import com.cabservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Loads a person for login. The identifier can be an email or a mobile number:
 * anything containing '@' is treated as an email.
 * Defining this bean also removes Spring's default "generated security password".
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String value = identifier.trim();

        User user = (value.contains("@")
                ? userRepository.findByEmail(value.toLowerCase(Locale.ROOT))
                : userRepository.findByMobile(value))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return UserPrincipal.from(user);
    }
}