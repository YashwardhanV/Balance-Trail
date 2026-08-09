package com.balancetrail.security;

import com.balancetrail.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
  private final AppUserRepository userRepository;

  public DatabaseUserDetailsService(AppUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var user =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    return User.withUsername(user.getUsername())
        .password(user.getPasswordHash())
        .roles(user.getRole().name())
        .disabled(!user.isEnabled())
        .build();
  }
}
