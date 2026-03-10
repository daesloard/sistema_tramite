package com.sistema.tramites.backend.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {
    List<WebPushSubscription> findAllByUsuarioIdAndActivoTrue(Long usuarioId);
    Optional<WebPushSubscription> findByUsuarioIdAndEndpoint(Long usuarioId, String endpoint);
}
