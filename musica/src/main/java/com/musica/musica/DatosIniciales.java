package com.musica.musica;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatosIniciales implements CommandLineRunner {

	private final MusicaRepository musicaRepository;
	private final UsuarioRepository usuarioRepository;

	public DatosIniciales(MusicaRepository musicaRepository, UsuarioRepository usuarioRepository) {
		this.musicaRepository = musicaRepository;
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	public void run(String... args) {
		crearUsuariosDePrueba();
		crearCancionesIniciales();
	}

	private void crearUsuariosDePrueba() {
		if (usuarioRepository.findByCorreoIgnoreCase("admin@musica.com").isEmpty()) {
			usuarioRepository.save(new Usuario(
					"Administrador",
					"Principal",
					"Musica",
					"admin@musica.com",
					"admin123",
					Rol.ADMIN,
					"Rock"
			));
		}

		if (usuarioRepository.findByCorreoIgnoreCase("usuario@musica.com").isEmpty()) {
			usuarioRepository.save(new Usuario(
					"Usuario",
					"Demo",
					"Musica",
					"usuario@musica.com",
					"usuario123",
					Rol.USUARIO,
					"Pop"
			));
		}
	}

	private void crearCancionesIniciales() {
		if (musicaRepository.count() > 0) {
			musicaRepository.findAll().forEach(cancion -> {
				if (cancion.getActivo() == null) {
					cancion.setActivo(true);
					musicaRepository.save(cancion);
				}
			});
			return;
		}

		musicaRepository.saveAll(List.of(
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=80",
						"Noche Electrica",
						"Luna Norte",
						"Pop",
						"3:42",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
						"Luces de Ciudad",
						2024,
						"Una cancion brillante para empezar con energia."
				),
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=900&q=80",
						"Ritmo de Medianoche",
						"Marco Sol",
						"Urbano",
						"4:05",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
						"Calles Vivas",
						2023,
						"Bajo profundo, voz suave y movimiento constante."
				),
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1501612780327-45045538702b?auto=format&fit=crop&w=900&q=80",
						"Corazon Acustico",
						"Valeria Cruz",
						"Balada",
						"3:28",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
						"Entre Cuerdas",
						2022,
						"Guitarra calida y una melodia directa."
				),
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=900&q=80",
						"Bajo el Sol",
						"Rafa Mar",
						"Reggae",
						"4:18",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
						"Verano Libre",
						2021,
						"Relajada, luminosa y perfecta para manejar."
				),
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=900&q=80",
						"Pulso Neon",
						"Nova Beat",
						"Electronica",
						"5:01",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
						"Frecuencia Azul",
						2025,
						"Sintetizadores fuertes y ambiente nocturno."
				),
				new Cancion(
						null,
						"https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?auto=format&fit=crop&w=900&q=80",
						"Camino al Escenario",
						"Los del Puerto",
						"Rock",
						"3:57",
						"https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
						"Kilometro Final",
						2020,
						"Guitarras al frente y bateria con fuerza."
				)
		));
	}
}
