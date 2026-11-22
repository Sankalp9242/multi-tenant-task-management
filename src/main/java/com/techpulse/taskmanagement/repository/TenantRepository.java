package com.techpulse.taskmanagement.repository;

import com.techpulse.taskmanagement.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
