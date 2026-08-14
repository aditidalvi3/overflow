package com.orderflow.orderservice.config;

import com.orderflow.orderservice.entity.Role;
import com.orderflow.orderservice.entity.User;
import com.orderflow.orderservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${orderflow.admin.email}") String adminEmail,
                                 @Value("${orderflow.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(new User(adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN));
        }
    }
}
