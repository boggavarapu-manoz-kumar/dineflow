package com.dineflow.identity.repository;

import com.dineflow.identity.domain.LoginAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLogEntity, UUID> {}
