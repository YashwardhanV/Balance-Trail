package com.balancetrail.controller;

import com.balancetrail.dto.CurrentUserResponse;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  @GetMapping("/me")
  public ResponseEntity<CurrentUserResponse> me(Principal principal, Authentication authentication) {
    String role =
        authentication.getAuthorities().stream()
            .findFirst()
            .map(authority -> authority.getAuthority().replace("ROLE_", ""))
            .orElse("ANALYST");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CurrentUserResponse(principal.getName(), role));
  }
}
