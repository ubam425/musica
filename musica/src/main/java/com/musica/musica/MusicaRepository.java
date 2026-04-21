package com.musica.musica;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicaRepository extends JpaRepository<Cancion, Long> {

	List<Cancion> findByActivoTrue();

	List<Cancion> findByActivoTrueAndGeneroIgnoreCase(String genero);

	List<Cancion> findByActivoTrueAndNombreContainingIgnoreCaseOrActivoTrueAndActorContainingIgnoreCaseOrActivoTrueAndGeneroContainingIgnoreCase(
			String nombre,
			String actor,
			String genero
	);
}
