package com.balancetrail.repository;

import com.balancetrail.entity.GatewayFormatEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayFormatRepository extends JpaRepository<GatewayFormatEntity, Long> {

  @EntityGraph(attributePaths = "createdBy")
  List<GatewayFormatEntity> findAllByOrderByNameAsc();

  boolean existsByNameIgnoreCase(String name);
}
