package com.balancetrail.repository;

import com.balancetrail.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
  Optional<AppUserEntity> findByUsernameIgnoreCase(String username);
}
