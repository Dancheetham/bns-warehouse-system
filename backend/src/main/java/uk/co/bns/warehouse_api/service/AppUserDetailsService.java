package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String name) {
        uk.co.bns.warehouse_api.entity.User user = userRepository.findByName(name)
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + name));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getName())
                .password(user.getPasswordHash())
                .authorities("USER")
                .build();
    }
}
