package com.musica.musica;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/mobile")
public class MobileApiController {

	private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%&*";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UsuarioRepository usuarioRepository;
	private final MusicaRepository musicaRepository;
	private final CorreoService correoService;

	public MobileApiController(
			UsuarioRepository usuarioRepository,
			MusicaRepository musicaRepository,
			CorreoService correoService
	) {
		this.usuarioRepository = usuarioRepository;
		this.musicaRepository = musicaRepository;
		this.correoService = correoService;
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<UsuarioResponse>> login(@RequestBody LoginRequest request) {
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCaseAndActivoTrue(limpiar(request.correo()));
		if (usuario.isEmpty() || !usuario.get().getContrasena().equals(request.contrasena())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ApiResponse<>(false, "Correo o contrasena incorrectos.", null));
		}
		return ResponseEntity.ok(new ApiResponse<>(true, "Login correcto.", UsuarioResponse.from(usuario.get())));
	}

	@PostMapping("/registro")
	public ResponseEntity<ApiResponse<UsuarioResponse>> registro(@RequestBody RegistroRequest request) {
		String correo = limpiar(request.correo());
		if (usuarioRepository.findByCorreoIgnoreCase(correo).isPresent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiResponse<>(false, "Ese correo ya esta registrado.", null));
		}

		Usuario usuario = new Usuario(
				limpiar(request.nombres()),
				limpiar(request.apellidoP()),
				limpiar(request.apellidoM()),
				correo,
				generarContrasena(),
				Rol.USUARIO,
				limpiar(request.generoFavorito())
		);

		try {
			correoService.enviarCredenciales(usuario);
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
					.body(new ApiResponse<>(false, ex.getMessage(), null));
		}

		usuarioRepository.save(usuario);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(true, "Usuario creado. La contrasena se envio por correo.", UsuarioResponse.from(usuario)));
	}

	@PostMapping("/recuperar")
	public ResponseEntity<ApiResponse<Void>> recuperar(@RequestBody RecuperarRequest request) {
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(limpiar(request.correo()));
		if (usuario.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "Si el correo existe, se enviaron los datos.", null));
		}
		try {
			correoService.enviarRecuperacion(usuario.get());
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
					.body(new ApiResponse<>(false, ex.getMessage(), null));
		}
		return ResponseEntity.ok(new ApiResponse<>(true, "Datos enviados al correo.", null));
	}

	@GetMapping("/usuarios/{id}")
	public UsuarioResponse usuario(@PathVariable Long id) {
		return UsuarioResponse.from(usuarioRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
	}

	@PostMapping("/usuarios/{id}/genero")
	public ApiResponse<UsuarioResponse> cambiarGenero(@PathVariable Long id, @RequestBody GeneroRequest request) {
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
		usuario.setGeneroFavorito(limpiar(request.generoFavorito()));
		usuarioRepository.save(usuario);
		return new ApiResponse<>(true, "Genero actualizado.", UsuarioResponse.from(usuario));
	}

	@GetMapping("/canciones")
	public List<CancionResponse> canciones(HttpServletRequest request) {
		return musicaRepository.findByActivoTrue().stream()
				.sorted(Comparator.comparing(Cancion::getId))
				.map(cancion -> CancionResponse.from(cancion, baseUrl(request)))
				.toList();
	}

	@GetMapping("/canciones/{id}")
	public CancionResponse cancion(@PathVariable Long id, HttpServletRequest request) {
		Cancion cancion = musicaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no encontrada"));
		if (!Boolean.TRUE.equals(cancion.getActivo())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no disponible");
		}
		return CancionResponse.from(cancion, baseUrl(request));
	}

	@GetMapping("/canciones/{id}/audio")
	public ResponseEntity<?> audio(@PathVariable Long id) throws IOException {
		Cancion cancion = musicaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no encontrada"));
		if (!Boolean.TRUE.equals(cancion.getActivo())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no disponible");
		}
		if (!cancion.tieneAudioEnBaseDatos()) {
			if (vacio(cancion.getMusica())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cancion no tiene audio");
			}
			return ResponseEntity.status(HttpStatus.FOUND)
					.location(URI.create(cancion.getMusica()))
					.build();
		}
		String nombre = vacio(cancion.getAudioNombre()) ? "cancion-" + id + ".mp3" : cancion.getAudioNombre();
		String tipo = vacio(cancion.getAudioTipo()) ? "audio/mpeg" : cancion.getAudioTipo();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replace("\"", "") + "\"")
				.contentType(MediaType.parseMediaType(tipo))
				.body(cancion.getAudioDatos());
	}

	@GetMapping("/recomendaciones/{usuarioId}")
	public List<CancionResponse> recomendaciones(@PathVariable Long usuarioId, HttpServletRequest request) {
		Usuario usuario = usuarioRepository.findById(usuarioId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
		List<Cancion> canciones = vacio(usuario.getGeneroFavorito())
				? musicaRepository.findByActivoTrue()
				: musicaRepository.findByActivoTrueAndGeneroIgnoreCase(usuario.getGeneroFavorito());
		return canciones.stream()
				.sorted(Comparator.comparing(Cancion::getId))
				.map(cancion -> CancionResponse.from(cancion, baseUrl(request)))
				.toList();
	}

	@GetMapping("/buscar")
	public List<CancionResponse> buscar(@RequestParam(defaultValue = "") String q, HttpServletRequest request) {
		String texto = q.toLowerCase(Locale.ROOT).trim();
		return musicaRepository.findByActivoTrue().stream()
				.filter(cancion -> texto.isBlank()
						|| contiene(cancion.getNombre(), texto)
						|| contiene(cancion.getActor(), texto)
						|| contiene(cancion.getGenero(), texto)
						|| contiene(cancion.getAlbum(), texto))
				.sorted(Comparator.comparing(Cancion::getId))
				.map(cancion -> CancionResponse.from(cancion, baseUrl(request)))
				.toList();
	}

	@GetMapping("/generos")
	public List<String> generos() {
		return List.of("Pop", "Urbano", "Balada", "Reggae", "Electronica", "Rock", "Regional", "Jazz");
	}

	private String generarContrasena() {
		StringBuilder contrasena = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			contrasena.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
		}
		return contrasena.toString();
	}

	private String baseUrl(HttpServletRequest request) {
		String port = request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort();
		return request.getScheme() + "://" + request.getServerName() + port;
	}

	private String limpiar(String value) {
		return value == null ? "" : value.trim();
	}

	private boolean vacio(String value) {
		return value == null || value.isBlank();
	}

	private boolean contiene(String value, String texto) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(texto);
	}

	public record ApiResponse<T>(boolean ok, String mensaje, T data) {
	}

	public record LoginRequest(String correo, String contrasena) {
	}

	public record RegistroRequest(String nombres, String apellidoP, String apellidoM, String correo, String generoFavorito) {
	}

	public record RecuperarRequest(String correo) {
	}

	public record GeneroRequest(String generoFavorito) {
	}

	public record UsuarioResponse(
			Long id,
			String nombres,
			String apellidoP,
			String apellidoM,
			String nombreCompleto,
			String correo,
			Rol rol,
			String generoFavorito,
			Boolean activo
	) {
		static UsuarioResponse from(Usuario usuario) {
			return new UsuarioResponse(
					usuario.getId(),
					usuario.getNombres(),
					usuario.getApellidoP(),
					usuario.getApellidoM(),
					usuario.getNombreCompleto(),
					usuario.getCorreo(),
					usuario.getRol(),
					usuario.getGeneroFavorito(),
					usuario.getActivo()
			);
		}
	}

	public record CancionResponse(
			Long id,
			String imagen,
			String nombre,
			String actor,
			String genero,
			String duracion,
			String album,
			Integer anio,
			String descripcion,
			Boolean activo,
			String audioUrl
	) {
		static CancionResponse from(Cancion cancion, String baseUrl) {
			String audioUrl = cancion.tieneAudioEnBaseDatos()
					? baseUrl + "/api/mobile/canciones/" + cancion.getId() + "/audio"
					: cancion.getMusica();
			return new CancionResponse(
					cancion.getId(),
					cancion.getImagen(),
					cancion.getNombre(),
					cancion.getActor(),
					cancion.getGenero(),
					cancion.getDuracion(),
					cancion.getAlbum(),
					cancion.getAnio(),
					cancion.getDescripcion(),
					cancion.getActivo(),
					audioUrl
			);
		}
	}
}
