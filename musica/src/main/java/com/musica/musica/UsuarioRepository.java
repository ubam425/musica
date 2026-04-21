package com.musica.musica;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	Optional<Usuario> findByCorreoIgnoreCaseAndActivoTrue(String correo);
}
