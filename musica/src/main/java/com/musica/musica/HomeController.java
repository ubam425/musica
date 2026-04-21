package com.musica.musica;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class HomeController {

	private static final String APP_NAME = "MusicUbam";
	private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%&*";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final MusicaRepository musicaRepository;
	private final UsuarioRepository usuarioRepository;
	private final CorreoService correoService;

	public HomeController(
			MusicaRepository musicaRepository,
			UsuarioRepository usuarioRepository,
			CorreoService correoService
	) {
		this.musicaRepository = musicaRepository;
		this.usuarioRepository = usuarioRepository;
		this.correoService = correoService;
	}

	@GetMapping("/")
	public RedirectView inicio(HttpSession session) {
		Usuario usuario = usuarioActual(session).orElse(null);
		if (usuario == null) {
			return new RedirectView("/login");
		}
		if (usuario.getRol() == Rol.ADMIN) {
			return new RedirectView("/admin");
		}
		return new RedirectView("/app");
	}

	@GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String login(@RequestParam(defaultValue = "") String error) {
		String mensaje = error.isBlank() ? "" : "<p class=\"error\">Correo o contrasena incorrectos.</p>";
		return page("Login", """
				<main class="center">
					<section class="login-card">
						<p class="label">%s</p>
						<h1>Iniciar sesion</h1>
						<p>Entra para escuchar musica o administrar el catalogo.</p>
						%s
						<form method="post" action="/login" class="stack">
							<label>Correo
								<input name="correo" type="email" required placeholder="usuario@musica.com">
							</label>
							<label>Contrasena
								<input name="contrasena" type="password" required placeholder="Tu contrasena">
							</label>
							<button type="submit"><span class="btn-icon">&#10148;</span><span>Entrar</span></button>
						</form>
						<a class="secondary-button" href="/registro"><span class="btn-icon">&#10010;</span><span>Registrarte como usuario</span></a>
						<a class="secondary-button" href="/recuperar"><span class="btn-icon">&#9993;</span><span>Recuperar contrasena</span></a>
						<div class="hint">
							<strong>Admin:</strong> admin@musica.com / admin123<br>
							<strong>Usuario:</strong> usuario@musica.com / usuario123
						</div>
					</section>
				</main>
				""".formatted(APP_NAME, mensaje));
	}

	@GetMapping(value = "/registro", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String registro(
			@RequestParam(defaultValue = "") String error,
			@RequestParam(defaultValue = "") String motivo
	) {
		String mensaje = switch (error) {
			case "correo" -> "<p class=\"error\">Ese correo ya esta registrado. Intenta iniciar sesion.</p>";
			case "correo-envio" -> "<p class=\"error\">No se pudo enviar el correo. No se guardo el usuario.<br>" + html(motivo) + "</p>";
			default -> "";
		};
		return page("Registro", """
				<main class="center">
					<section class="login-card">
						<p class="label">%s</p>
						<h1>Crear cuenta</h1>
						<p>Tu cuenta se crea como usuario para escuchar musica y recibir recomendaciones.</p>
						%s
						<form method="post" action="/registro" class="stack">
							<label>Nombre(s)
								<input name="nombres" required placeholder="Eduardo">
							</label>
							<label>Apellido paterno
								<input name="apellidoP" required placeholder="Perez">
							</label>
							<label>Apellido materno
								<input name="apellidoM" required placeholder="Lopez">
							</label>
							<label>Correo
								<input name="correo" type="email" required placeholder="tu@correo.com">
							</label>
							<label>Genero favorito
								<select name="generoFavorito" required>%s</select>
							</label>
							<button type="submit"><span class="btn-icon">&#10010;</span><span>Crear cuenta</span></button>
						</form>
						<p class="hint">La contrasena se genera automaticamente y se envia a tu correo.</p>
						<a class="secondary-button" href="/login"><span class="btn-icon">&#8592;</span><span>Ya tengo cuenta</span></a>
					</section>
				</main>
				""".formatted(APP_NAME, mensaje, opcionesGenero("Pop")));
	}

	@PostMapping("/registro")
	public RedirectView registrarUsuarioPublico(
			@RequestParam String nombres,
			@RequestParam String apellidoP,
			@RequestParam String apellidoM,
			@RequestParam String correo,
			@RequestParam String generoFavorito,
			HttpSession session
	) {
		String correoLimpio = correo.trim();
		if (usuarioRepository.findByCorreoIgnoreCase(correoLimpio).isPresent()) {
			return new RedirectView("/registro?error=correo");
		}
		String contrasena = generarContrasena();
		Usuario usuario = new Usuario(
				nombres.trim(),
				apellidoP.trim(),
				apellidoM.trim(),
				correoLimpio,
				contrasena,
				Rol.USUARIO,
				generoFavorito
		);
		try {
			correoService.enviarCredenciales(usuario);
		} catch (IllegalStateException ex) {
			return new RedirectView("/registro?error=correo-envio&motivo=" + url(ex.getMessage()));
		}
		usuarioRepository.save(usuario);
		session.setAttribute("usuarioId", usuario.getId());
		session.setAttribute("rol", Rol.USUARIO.name());
		return new RedirectView("/app");
	}

	@GetMapping(value = "/recuperar", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String recuperar(
			@RequestParam(defaultValue = "") String estado,
			@RequestParam(defaultValue = "") String motivo
	) {
		String mensaje = switch (estado) {
			case "enviado" -> "<p class=\"success\">Si el correo existe, se enviaron tus datos de acceso.</p>";
			case "error" -> "<p class=\"error\">No se pudo enviar el correo.<br>" + html(motivo) + "</p>";
			default -> "";
		};
		return page("Recuperar contrasena", """
				<main class="center">
					<section class="login-card">
						<p class="label">%s</p>
						<h1>Recuperar contrasena</h1>
						<p>Ingresa tu correo y te mandaremos nuevamente tu nombre, correo y contrasena.</p>
						%s
						<form method="post" action="/recuperar" class="stack">
							<label>Correo
								<input name="correo" type="email" required placeholder="usuario@correo.com">
							</label>
							<button type="submit"><span class="btn-icon">&#9993;</span><span>Enviar datos</span></button>
						</form>
						<a class="secondary-button" href="/login"><span class="btn-icon">&#8592;</span><span>Volver al login</span></a>
					</section>
				</main>
				""".formatted(APP_NAME, mensaje));
	}

	@PostMapping("/recuperar")
	public RedirectView enviarRecuperacion(@RequestParam String correo) {
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(correo.trim());
		if (usuario.isPresent()) {
			try {
				correoService.enviarRecuperacion(usuario.get());
			} catch (IllegalStateException ex) {
				return new RedirectView("/recuperar?estado=error&motivo=" + url(ex.getMessage()));
			}
		}
		return new RedirectView("/recuperar?estado=enviado");
	}

	@PostMapping("/login")
	public RedirectView autenticar(
			@RequestParam String correo,
			@RequestParam String contrasena,
			HttpSession session
	) {
		Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCaseAndActivoTrue(correo.trim());
		if (usuario.isEmpty() || !usuario.get().getContrasena().equals(contrasena)) {
			return new RedirectView("/login?error=1");
		}

		session.setAttribute("usuarioId", usuario.get().getId());
		session.setAttribute("rol", usuario.get().getRol().name());

		if (usuario.get().getRol() == Rol.ADMIN) {
			return new RedirectView("/admin");
		}
		if (vacio(usuario.get().getGeneroFavorito())) {
			return new RedirectView("/preferencias");
		}
		return new RedirectView("/app");
	}

	@GetMapping("/logout")
	public RedirectView logout(HttpSession session) {
		session.invalidate();
		return new RedirectView("/login");
	}

	@GetMapping(value = "/preferencias", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String preferencias(HttpSession session) {
		Usuario usuario = usuarioActual(session).orElse(null);
		if (usuario == null) {
			return redirectScript("/login");
		}
		return page("Preferencias", """
				<main class="center">
					<section class="login-card">
						<p class="label">Primer acceso</p>
						<h1>Hola, %s</h1>
						<p>Elige tu genero favorito para recomendarte canciones al iniciar.</p>
						<form method="post" action="/preferencias" class="stack">
							<label>Genero favorito
								<select name="generoFavorito" required>
									%s
								</select>
							</label>
							<button type="submit">Guardar preferencias</button>
						</form>
					</section>
				</main>
				""".formatted(html(usuario.getNombres()), opcionesGenero(usuario.getGeneroFavorito())));
	}

	@PostMapping("/preferencias")
	public RedirectView guardarPreferencias(@RequestParam String generoFavorito, HttpSession session) {
		Usuario usuario = requireUsuario(session);
		usuario.setGeneroFavorito(generoFavorito);
		usuarioRepository.save(usuario);
		return new RedirectView("/app");
	}

	@GetMapping(value = "/app", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String app(
			@RequestParam(defaultValue = "inicio") String tab,
			@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) Long song,
			HttpSession session
	) {
		Usuario usuario = usuarioActual(session).orElse(null);
		if (usuario == null) {
			return redirectScript("/login");
		}
		if (usuario.getRol() == Rol.ADMIN) {
			return redirectScript("/admin");
		}
		if (vacio(usuario.getGeneroFavorito())) {
			return redirectScript("/preferencias");
		}

		List<Cancion> canciones = musicaRepository.findByActivoTrue();
		List<Cancion> recomendadas = canciones.stream()
				.filter(cancion -> igual(cancion.getGenero(), usuario.getGeneroFavorito()))
				.toList();
		List<Cancion> busqueda = buscar(canciones, q);
		List<Cancion> listaActual = switch (tab) {
			case "buscar" -> busqueda;
			case "biblioteca" -> canciones;
			default -> recomendadas.isEmpty() ? canciones : recomendadas;
		};
		Cancion seleccionada = cancionSeleccionada(canciones, song);

		return page(APP_NAME, vistaUsuario(usuario, canciones, recomendadas, listaActual, seleccionada, tab, q));
	}

	@PostMapping("/perfil/genero")
	public RedirectView cambiarGenero(@RequestParam String generoFavorito, HttpSession session) {
		Usuario usuario = requireRol(session, Rol.USUARIO);
		usuario.setGeneroFavorito(generoFavorito);
		usuarioRepository.save(usuario);
		return new RedirectView("/app?tab=perfil");
	}

	@GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	public String admin(
			@RequestParam(required = false) Long editar,
			@RequestParam(defaultValue = "") String mailError,
			HttpSession session
	) {
		Usuario usuario = usuarioActual(session).orElse(null);
		if (usuario == null) {
			return redirectScript("/login");
		}
		if (usuario.getRol() != Rol.ADMIN) {
			return redirectScript("/app");
		}
		Cancion cancion = editar == null ? new Cancion() : musicaRepository.findById(editar).orElse(new Cancion());
		List<Cancion> canciones = musicaRepository.findAll().stream()
				.sorted(Comparator.comparing(Cancion::getId))
				.toList();
		List<Usuario> usuarios = usuarioRepository.findAll().stream()
				.sorted(Comparator.comparing(Usuario::getId))
				.toList();

		String mensaje = mailError.isBlank()
				? ""
				: "<p class=\"error\">No se pudo enviar el correo. No se guardo el usuario.<br>" + html(mailError) + "</p>";

		return page("Admin", """
				<nav class="topbar">
					<strong>%s - Panel admin</strong>
					<div class="navlinks"><a href="/admin"><span class="btn-icon">&#9881;</span> Admin</a><a href="/app"><span class="btn-icon">&#9835;</span> Vista usuario</a><a href="/logout"><span class="btn-icon">&#10162;</span> Salir</a></div>
				</nav>
				<main class="shell">
					<section class="section">
						<h1>Administracion</h1>
						<p>Registra usuarios, crea canciones y administra el catalogo.</p>
						%s
					</section>
					<section class="two-columns">
						<div class="panel">
							<h2>%s cancion</h2>
							<form method="post" action="/admin/canciones" class="stack" enctype="multipart/form-data">
								<input type="hidden" name="id" value="%s">
								<label>Imagen <input name="imagen" value="%s" required></label>
								<label>Nombre <input name="nombre" value="%s" required></label>
								<label>Actor <input name="actor" value="%s" required></label>
								<label>Genero <select name="genero">%s</select></label>
								<label>Duracion <input name="duracion" value="%s" required placeholder="3:40"></label>
								<label>Archivo MP3 <input name="audioFile" type="file" accept="audio/mpeg,.mp3" %s></label>
								<label>Album <input name="album" value="%s"></label>
								<label>Anio <input name="anio" type="number" value="%s"></label>
								<label>Descripcion <textarea name="descripcion">%s</textarea></label>
								<button><span class="btn-icon">&#128190;</span><span>Guardar cancion</span></button>
							</form>
						</div>
						<div class="panel">
							<h2>Registrar usuario</h2>
							<form method="post" action="/admin/usuarios" class="stack">
								<label>Nombre(s) <input name="nombres" required></label>
								<label>Apellido paterno <input name="apellidoP" required></label>
								<label>Apellido materno <input name="apellidoM" required></label>
								<label>Correo <input name="correo" type="email" required></label>
								<label>Rol <select name="rol"><option>USUARIO</option><option>ADMIN</option></select></label>
								<label>Genero favorito <select name="generoFavorito">%s</select></label>
								<button><span class="btn-icon">&#10010;</span><span>Registrar usuario</span></button>
							</form>
							<p class="hint">La contrasena se genera automaticamente y se envia por correo.</p>
						</div>
					</section>
					<section class="section">
						<h2>Canciones</h2>
						<div class="table-wrap">%s</div>
					</section>
					<section class="section">
						<h2>Usuarios registrados</h2>
						<div class="table-wrap">%s</div>
					</section>
				</main>
				""".formatted(
				APP_NAME,
				mensaje,
				cancion.getId() == null ? "Nueva" : "Modificar",
				val(cancion.getId()),
				html(cancion.getImagen()),
				html(cancion.getNombre()),
				html(cancion.getActor()),
				opcionesGenero(cancion.getGenero()),
				html(cancion.getDuracion()),
				cancion.getId() == null || !cancion.tieneAudioEnBaseDatos() ? "required" : "",
				html(cancion.getAlbum()),
				val(cancion.getAnio()),
				html(cancion.getDescripcion()),
				opcionesGenero("Pop"),
				tablaCanciones(canciones),
				tablaUsuarios(usuarios)
		));
	}

	@PostMapping("/admin/usuarios")
	public RedirectView crearUsuario(
			@RequestParam String nombres,
			@RequestParam String apellidoP,
			@RequestParam String apellidoM,
			@RequestParam String correo,
			@RequestParam Rol rol,
			@RequestParam(required = false) String generoFavorito,
			HttpSession session
	) {
		requireRol(session, Rol.ADMIN);
		if (usuarioRepository.findByCorreoIgnoreCase(correo).isEmpty()) {
			String contrasena = generarContrasena();
			Usuario usuario = new Usuario(nombres, apellidoP, apellidoM, correo.trim(), contrasena, rol, generoFavorito);
			try {
				correoService.enviarCredenciales(usuario);
			} catch (IllegalStateException ex) {
				return new RedirectView("/admin?mailError=" + url(ex.getMessage()));
			}
			usuarioRepository.save(usuario);
		}
		return new RedirectView("/admin");
	}

	@PostMapping("/admin/canciones")
	public RedirectView guardarCancion(
			@RequestParam(required = false) Long id,
			@RequestParam String imagen,
			@RequestParam String nombre,
			@RequestParam String actor,
			@RequestParam String genero,
			@RequestParam String duracion,
			@RequestParam(required = false) MultipartFile audioFile,
			@RequestParam(required = false) String album,
			@RequestParam(required = false) Integer anio,
			@RequestParam(required = false) String descripcion,
			HttpSession session
	) throws IOException {
		requireRol(session, Rol.ADMIN);
		Cancion cancion = id == null ? new Cancion() : musicaRepository.findById(id).orElse(new Cancion());
		cancion.setImagen(imagen);
		cancion.setNombre(nombre);
		cancion.setActor(actor);
		cancion.setGenero(genero);
		cancion.setDuracion(duracion);
		cancion.setAlbum(album);
		cancion.setAnio(anio);
		cancion.setDescripcion(descripcion);
		if (audioFile != null && !audioFile.isEmpty()) {
			if (!esMp3(audioFile)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten archivos MP3");
			}
			cancion.setAudioNombre(audioFile.getOriginalFilename());
			cancion.setAudioTipo(vacio(audioFile.getContentType()) ? "audio/mpeg" : audioFile.getContentType());
			cancion.setAudioDatos(audioFile.getBytes());
			cancion.setMusica(null);
		}
		if (cancion.getActivo() == null) {
			cancion.setActivo(true);
		}
		musicaRepository.save(cancion);
		return new RedirectView("/admin");
	}

	@PostMapping("/admin/canciones/{id}/toggle")
	public RedirectView activarCancion(@PathVariable Long id, HttpSession session) {
		requireRol(session, Rol.ADMIN);
		Cancion cancion = musicaRepository.findById(id).orElseThrow();
		cancion.setActivo(!Boolean.TRUE.equals(cancion.getActivo()));
		musicaRepository.save(cancion);
		return new RedirectView("/admin");
	}

	@PostMapping("/admin/canciones/{id}/delete")
	public RedirectView eliminarCancion(@PathVariable Long id, HttpSession session) {
		requireRol(session, Rol.ADMIN);
		musicaRepository.deleteById(id);
		return new RedirectView("/admin");
	}

	@PostMapping("/admin/usuarios/{id}/toggle")
	public RedirectView activarUsuario(@PathVariable Long id, HttpSession session) {
		requireRol(session, Rol.ADMIN);
		Usuario usuario = usuarioRepository.findById(id).orElseThrow();
		usuario.setActivo(!Boolean.TRUE.equals(usuario.getActivo()));
		usuarioRepository.save(usuario);
		return new RedirectView("/admin");
	}

	@PostMapping("/admin/usuarios/{id}/delete")
	public RedirectView eliminarUsuario(@PathVariable Long id, HttpSession session) {
		Usuario admin = requireRol(session, Rol.ADMIN);
		if (!admin.getId().equals(id)) {
			usuarioRepository.deleteById(id);
		}
		return new RedirectView("/admin");
	}

	@GetMapping("/api/musica")
	@ResponseBody
	public List<Cancion> canciones() {
		return musicaRepository.findByActivoTrue();
	}

	@GetMapping("/api/musica/{id}")
	@ResponseBody
	public Cancion cancion(@PathVariable Long id) {
		return musicaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no encontrada: " + id));
	}

	@GetMapping("/musica/archivo/{id}")
	public ResponseEntity<byte[]> archivoMusica(@PathVariable Long id) {
		Cancion cancion = musicaRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancion no encontrada: " + id));
		if (!cancion.tieneAudioEnBaseDatos()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La cancion no tiene archivo MP3 guardado");
		}
		String nombre = vacio(cancion.getAudioNombre()) ? "cancion-" + id + ".mp3" : cancion.getAudioNombre();
		String tipo = vacio(cancion.getAudioTipo()) ? "audio/mpeg" : cancion.getAudioTipo();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replace("\"", "") + "\"")
				.contentType(MediaType.parseMediaType(tipo))
				.body(cancion.getAudioDatos());
	}

	private Usuario requireUsuario(HttpSession session) {
		return usuarioActual(session).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
	}

	private Usuario requireRol(HttpSession session, Rol rol) {
		Usuario usuario = requireUsuario(session);
		if (usuario.getRol() != rol) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		return usuario;
	}

	private Optional<Usuario> usuarioActual(HttpSession session) {
		Object usuarioId = session.getAttribute("usuarioId");
		if (usuarioId instanceof Long id) {
			return usuarioRepository.findById(id);
		}
		return Optional.empty();
	}

	private String redirectScript(String url) {
		return """
				<!doctype html>
				<html lang="es">
				<head>
					<meta charset="utf-8">
					<meta http-equiv="refresh" content="0;url=%s">
					<title>Redirigiendo</title>
				</head>
				<body>
					<script>window.location.replace('%s');</script>
					<p>Redirigiendo a <a href="%s">%s</a></p>
				</body>
				</html>
				""".formatted(html(url), js(url), html(url), html(url));
	}

	private String generarContrasena() {
		StringBuilder contrasena = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			contrasena.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
		}
		return contrasena.toString();
	}

	private void enviarCredenciales(Usuario usuario) {
		try {
			correoService.enviarCredenciales(usuario);
		} catch (IllegalStateException ex) {
			System.err.println("No se pudo enviar el correo de credenciales a " + usuario.getCorreo() + ": " + ex.getMessage());
		}
	}

	private boolean esMp3(MultipartFile archivo) {
		String nombre = archivo.getOriginalFilename();
		String tipo = archivo.getContentType();
		boolean nombreMp3 = nombre != null && nombre.toLowerCase(Locale.ROOT).endsWith(".mp3");
		boolean tipoMp3 = tipo == null || tipo.equalsIgnoreCase("audio/mpeg") || tipo.equalsIgnoreCase("audio/mp3");
		return nombreMp3 && tipoMp3;
	}

	private String audioSrc(Cancion cancion) {
		if (cancion != null && cancion.tieneAudioEnBaseDatos()) {
			return "/musica/archivo/" + cancion.getId();
		}
		return cancion == null ? "" : cancion.getMusica();
	}

	private List<Cancion> buscar(List<Cancion> canciones, String q) {
		if (vacio(q)) {
			return canciones;
		}
		String texto = q.toLowerCase(Locale.ROOT);
		return canciones.stream()
				.filter(cancion ->
						contiene(cancion.getNombre(), texto)
								|| contiene(cancion.getActor(), texto)
								|| contiene(cancion.getGenero(), texto)
				)
				.toList();
	}

	private String vistaUsuario(
			Usuario usuario,
			List<Cancion> canciones,
			List<Cancion> recomendadas,
			List<Cancion> listaActual,
			Cancion seleccionada,
			String tab,
			String q
	) {
		String titulo = switch (tab) {
			case "buscar" -> "Buscar";
			case "biblioteca" -> "Tu biblioteca";
			case "perfil" -> "Perfil";
			default -> "Inicio";
		};
		String contenidoCentral = "perfil".equals(tab)
				? perfilUsuario(usuario)
				: playlistCentral(usuario, listaActual, seleccionada, titulo, tab, q);

		return """
				<div class="music-app">
					<header class="music-top">
						<div class="window-dots"><span></span><span></span><span></span></div>
						<a class="round-icon" href="/app?tab=inicio" title="Inicio">&#8962;</a>
						<form class="music-search" method="get" action="/app">
							<input type="hidden" name="tab" value="buscar">
							<span>&#128269;</span>
							<input name="q" value="%s" placeholder="Que quieres reproducir?">
							<button title="Buscar">&#10148;</button>
						</form>
						<div class="music-actions">
							<a class="top-action" href="/app?tab=inicio" title="Inicio">&#8962;</a>
							<a class="top-action" href="/app?tab=biblioteca" title="Biblioteca">&#9835;</a>
							<a class="avatar" href="/app?tab=perfil">%s</a>
						</div>
					</header>
					<aside class="library-panel">
						<div class="library-head">
							<h2>%s</h2>
							<div><a href="/app?tab=buscar" title="Buscar">&#128269;</a><a href="/app?tab=biblioteca" title="Abrir">&#8599;</a></div>
						</div>
						<div class="chips"><a href="/app?tab=biblioteca">&#9835; Playlists</a><a href="/app?tab=inicio">&#9733; Recomendadas</a><a href="/app?tab=buscar">&#128269; Buscar</a></div>
						<div class="library-tools"><a href="/app?tab=buscar" title="Buscar">&#128269;</a><a href="/app?tab=biblioteca" title="Recientes">&#8635;</a></div>
						%s
					</aside>
					<section class="user-main">
						%s
					</section>
					<aside class="now-panel">
						%s
					</aside>
					<footer class="player-bar">
						<div class="mini-track">
							<img id="playerCover" src="%s" alt="">
							<div><strong id="playerTitle">%s</strong><span id="playerArtist">%s</span></div>
							<button class="icon-button" id="favoriteBtn" type="button" title="Marcar favorito">&#9825;</button>
						</div>
						<div class="player-center">
							<div class="controls">
								<button class="control-button" id="shuffleBtn" type="button" title="Aleatorio">&#8644;</button>
								<button class="control-button" id="prevBtn" type="button" title="Anterior">&#9198;</button>
								<button class="control-button main-control" id="playPauseBtn" type="button" title="Reproducir">&#9654;</button>
								<button class="control-button" id="nextBtn" type="button" title="Siguiente">&#9197;</button>
								<button class="control-button" id="repeatBtn" type="button" title="Repetir">&#8635;</button>
							</div>
							<div class="progress"><span id="currentTime">0:00</span><button id="progressBar" type="button"><i id="progressFill"></i></button><span id="durationTime">%s</span></div>
						</div>
						<div class="player-tools">
							<a class="tool-link" href="/app?tab=biblioteca" title="Lista">&#9776;</a>
							<a class="tool-link" href="/app?tab=perfil" title="Perfil">&#9787;</a>
							<button class="control-button" id="muteBtn" type="button" title="Volumen">&#128266;</button>
							<button class="volume" id="volumeBar" type="button"><i id="volumeFill"></i></button>
							<a class="tool-link" href="/logout" title="Salir">&#10162;</a>
						</div>
						<audio id="audioPlayer" preload="metadata" src="%s"></audio>
					</footer>
					<script>
						%s
					</script>
				</div>
				""".formatted(
				html(q),
				iniciales(usuario),
				APP_NAME,
				bibliotecaLateral(canciones, recomendadas, seleccionada, tab),
				contenidoCentral,
				panelDerecho(seleccionada),
				html(seleccionada.getImagen()),
				html(seleccionada.getNombre()),
				html(seleccionada.getActor()),
				html(seleccionada.getDuracion()),
				html(audioSrc(seleccionada)),
				playerScript(canciones, seleccionada)
		);
	}

	private String playlistCentral(
			Usuario usuario,
			List<Cancion> canciones,
			Cancion seleccionada,
			String titulo,
			String tab,
			String q
	) {
		boolean biblioteca = "biblioteca".equals(tab);
		String subtitulo = biblioteca
				? html(usuario.getNombreCompleto()) + " - " + canciones.size() + " canciones"
				: "Recomendadas por tu gusto: " + html(usuario.getGeneroFavorito());
		String tipo = "buscar".equals(tab) ? "Resultados" : biblioteca ? "Playlist" : "Para ti";
		String heroClass = biblioteca ? "library-hero" : "song-hero";
		String portada = biblioteca ? "" : "<img class=\"hero-cover\" src=\"%s\" alt=\"\">".formatted(html(seleccionada.getImagen()));
		String buscar = "buscar".equals(tab) ? """
				<form class="inline-search" method="get" action="/app">
					<input type="hidden" name="tab" value="buscar">
					<input name="q" value="%s" placeholder="Busca una cancion, artista o genero">
					<button><span class="btn-icon">&#128269;</span><span>Buscar</span></button>
				</form>
				""".formatted(html(q)) : "";

		return """
				<div class="main-scroll">
					<section class="hero %s">
						%s
						<div>
							<p class="hero-type">%s</p>
							<h1>%s</h1>
							<p><strong>%s</strong></p>
						</div>
					</section>
					<section class="list-surface">
						<div class="toolbar">
							<button class="play-big" id="heroPlayBtn" type="button" title="Reproducir">&#9654;</button>
							<button class="ghost action-button" id="heroShuffleBtn" type="button" title="Aleatorio">&#8644;</button>
							<a class="ghost action-button" id="openAudioBtn" href="%s" target="_blank" rel="noreferrer" title="Abrir audio">&#9835;</a>
							<a class="toolbar-right" href="/app?tab=biblioteca" title="Lista">&#9776;</a>
						</div>
						%s
						<div class="song-table">
							<div class="table-header"><span>#</span><span>Titulo</span><span>Album</span><span>Genero</span><span>Tiempo</span></div>
							%s
						</div>
					</section>
				</div>
				""".formatted(
				heroClass,
				portada,
				tipo,
				html(titulo),
				subtitulo,
				html(audioSrc(seleccionada)),
				buscar,
				filasCanciones(canciones, seleccionada, tab)
		);
	}

	private String perfilUsuario(Usuario usuario) {
		return """
				<div class="main-scroll">
					<section class="hero library-hero profile-hero">
						<div class="profile-avatar">%s</div>
						<div>
							<p class="hero-type">Perfil</p>
							<h1>%s</h1>
							<p>%s - Genero favorito: %s</p>
						</div>
					</section>
					<section class="list-surface profile-form">
						<h2>Preferencias</h2>
						<form method="post" action="/perfil/genero" class="stack">
							<label>Cambiar genero favorito
								<select name="generoFavorito">%s</select>
							</label>
							<button><span class="btn-icon">&#128190;</span><span>Actualizar perfil</span></button>
						</form>
						<a class="logout-link" href="/logout">Cerrar sesion</a>
					</section>
				</div>
				""".formatted(
				iniciales(usuario),
				html(usuario.getNombreCompleto()),
				html(usuario.getCorreo()),
				html(usuario.getGeneroFavorito()),
				opcionesGenero(usuario.getGeneroFavorito())
		);
	}

	private String bibliotecaLateral(List<Cancion> canciones, List<Cancion> recomendadas, Cancion seleccionada, String tab) {
		StringBuilder items = new StringBuilder();
		items.append("""
				<a class="library-item %s" href="/app?tab=biblioteca">
					<div class="saved-icon">B</div>
					<div><strong>Tu biblioteca</strong><span>Playlist - %s canciones</span></div>
				</a>
				""".formatted("biblioteca".equals(tab) ? "active" : "", canciones.size()));
		items.append("""
				<a class="library-item %s" href="/app?tab=inicio">
					<div class="liked-icon">R</div>
					<div><strong>Tus recomendaciones</strong><span>%s - %s canciones</span></div>
				</a>
				""".formatted("inicio".equals(tab) ? "active" : "", html(seleccionada.getGenero()), recomendadas.isEmpty() ? canciones.size() : recomendadas.size()));

		for (Cancion cancion : canciones.stream().limit(6).toList()) {
			items.append("""
					<a class="library-item %s" href="/app?tab=%s&song=%s">
						<img src="%s" alt="">
						<div><strong>%s</strong><span>%s - %s</span></div>
					</a>
					""".formatted(
					cancion.getId().equals(seleccionada.getId()) ? "active" : "",
					html(tab),
					cancion.getId(),
					html(cancion.getImagen()),
					html(cancion.getNombre()),
					html(cancion.getGenero()),
					html(cancion.getActor())
			));
		}
		return items.toString();
	}

	private String filasCanciones(List<Cancion> canciones, Cancion seleccionada, String tab) {
		if (canciones.isEmpty()) {
			return "<p class=\"empty-state\">No encontramos canciones con ese criterio.</p>";
		}
		StringBuilder filas = new StringBuilder();
		int index = 1;
		for (Cancion cancion : canciones) {
			boolean activa = cancion.getId().equals(seleccionada.getId());
			filas.append("""
					<a class="song-row %s" href="/app?tab=%s&song=%s" data-song-id="%s">
						<span class="row-index">%s</span>
						<span class="row-title"><img src="%s" alt=""><span><strong>%s</strong><em>%s</em></span></span>
						<span>%s</span>
						<span>%s</span>
						<span>%s</span>
					</a>
					""".formatted(
					activa ? "playing" : "",
					html(tab),
					cancion.getId(),
					cancion.getId(),
					activa ? ">" : index,
					html(cancion.getImagen()),
					html(cancion.getNombre()),
					html(cancion.getActor()),
					html(cancion.getAlbum()),
					html(cancion.getGenero()),
					html(cancion.getDuracion())
			));
			index++;
		}
		return filas.toString();
	}

	private String panelDerecho(Cancion cancion) {
		return """
				<div class="now-scroll">
					<h2>%s</h2>
					<img class="now-cover" src="%s" alt="">
					<div class="now-title">
						<div><h3>%s</h3><p>%s</p></div>
						<button class="icon-button favorite-toggle" type="button" title="Marcar favorito">&#9825;</button>
					</div>
					<div class="now-card">
						<h3>Detalles</h3>
						<p><strong>Album:</strong> %s</p>
						<p><strong>Genero:</strong> %s</p>
						<p><strong>Duracion:</strong> %s</p>
						<p>%s</p>
					</div>
					<h3>Videos musicales relacionados</h3>
					<div class="video-grid">
						<div></div><div></div>
					</div>
				</div>
				""".formatted(
				html(cancion.getNombre()),
				html(cancion.getImagen()),
				html(cancion.getNombre()),
				html(cancion.getActor()),
				html(cancion.getAlbum()),
				html(cancion.getGenero()),
				html(cancion.getDuracion()),
				html(cancion.getDescripcion())
		);
	}

	private Cancion cancionSeleccionada(List<Cancion> canciones, Long song) {
		if (canciones.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay canciones activas");
		}
		if (song == null) {
			return canciones.get(0);
		}
		return canciones.stream()
				.filter(cancion -> cancion.getId().equals(song))
				.findFirst()
				.orElse(canciones.get(0));
	}

	private String iniciales(Usuario usuario) {
		String nombres = vacio(usuario.getNombres()) ? "U" : usuario.getNombres().substring(0, 1);
		String apellido = vacio(usuario.getApellidoP()) ? "" : usuario.getApellidoP().substring(0, 1);
		return html((nombres + apellido).toUpperCase(Locale.ROOT));
	}

	private String playerScript(List<Cancion> canciones, Cancion seleccionada) {
		StringBuilder songs = new StringBuilder("[");
		for (int i = 0; i < canciones.size(); i++) {
			Cancion cancion = canciones.get(i);
			if (i > 0) {
				songs.append(",");
			}
			songs.append("{")
					.append("id:").append(cancion.getId()).append(",")
					.append("title:\"").append(js(cancion.getNombre())).append("\",")
					.append("artist:\"").append(js(cancion.getActor())).append("\",")
					.append("album:\"").append(js(cancion.getAlbum())).append("\",")
					.append("cover:\"").append(js(cancion.getImagen())).append("\",")
					.append("src:\"").append(js(audioSrc(cancion))).append("\",")
					.append("duration:\"").append(js(cancion.getDuracion())).append("\"")
					.append("}");
		}
		songs.append("]");

		int selectedIndex = 0;
		for (int i = 0; i < canciones.size(); i++) {
			if (canciones.get(i).getId().equals(seleccionada.getId())) {
				selectedIndex = i;
				break;
			}
		}

		return """
				const songs = %s;
				let currentIndex = %s;
				const audio = document.getElementById('audioPlayer');
				const cover = document.getElementById('playerCover');
				const title = document.getElementById('playerTitle');
				const artist = document.getElementById('playerArtist');
				const playPauseBtn = document.getElementById('playPauseBtn');
				const heroPlayBtn = document.getElementById('heroPlayBtn');
				const progressBar = document.getElementById('progressBar');
				const progressFill = document.getElementById('progressFill');
				const currentTime = document.getElementById('currentTime');
				const durationTime = document.getElementById('durationTime');
				const volumeBar = document.getElementById('volumeBar');
				const volumeFill = document.getElementById('volumeFill');
				let mutedBefore = 0.8;

				function formatTime(seconds) {
					if (!Number.isFinite(seconds)) return '0:00';
					const minutes = Math.floor(seconds / 60);
					const rest = Math.floor(seconds %% 60).toString().padStart(2, '0');
					return minutes + ':' + rest;
				}

				function loadSong(index, shouldPlay) {
					if (!songs.length) return;
					currentIndex = (index + songs.length) %% songs.length;
					const song = songs[currentIndex];
					audio.src = song.src;
					cover.src = song.cover;
					title.textContent = song.title;
					artist.textContent = song.artist;
					durationTime.textContent = song.duration || '0:00';
					document.querySelectorAll('.song-row').forEach(row => row.classList.toggle('playing', Number(row.dataset.songId) === song.id));
					if (shouldPlay) audio.play();
				}

				function togglePlay() {
					if (audio.paused) audio.play(); else audio.pause();
				}

				function setPlayState() {
					const icon = audio.paused ? '&#9654;' : '&#10073;&#10073;';
					playPauseBtn.innerHTML = icon;
					if (heroPlayBtn) heroPlayBtn.innerHTML = icon;
				}

				function seekFromEvent(event) {
					const rect = progressBar.getBoundingClientRect();
					const pct = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
					if (Number.isFinite(audio.duration)) audio.currentTime = audio.duration * pct;
				}

				function volumeFromEvent(event) {
					const rect = volumeBar.getBoundingClientRect();
					const pct = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
					audio.volume = pct;
					audio.muted = pct === 0;
					volumeFill.style.width = Math.round(pct * 100) + '%%';
				}

				playPauseBtn.addEventListener('click', togglePlay);
				if (heroPlayBtn) heroPlayBtn.addEventListener('click', togglePlay);
				document.getElementById('nextBtn').addEventListener('click', () => loadSong(currentIndex + 1, true));
				document.getElementById('prevBtn').addEventListener('click', () => loadSong(currentIndex - 1, true));
				document.getElementById('shuffleBtn').addEventListener('click', () => loadSong(Math.floor(Math.random() * songs.length), true));
				document.getElementById('heroShuffleBtn')?.addEventListener('click', () => loadSong(Math.floor(Math.random() * songs.length), true));
				document.getElementById('repeatBtn').addEventListener('click', () => {
					audio.loop = !audio.loop;
					document.getElementById('repeatBtn').classList.toggle('active', audio.loop);
				});
				document.getElementById('favoriteBtn').addEventListener('click', event => event.currentTarget.classList.toggle('active'));
				document.querySelectorAll('.favorite-toggle').forEach(button => {
					button.addEventListener('click', event => event.currentTarget.classList.toggle('active'));
				});
				document.getElementById('muteBtn').addEventListener('click', () => {
					if (audio.muted || audio.volume === 0) {
						audio.muted = false;
						audio.volume = mutedBefore;
					} else {
						mutedBefore = audio.volume || 0.8;
						audio.muted = true;
					}
					volumeFill.style.width = audio.muted ? '0%%' : Math.round(audio.volume * 100) + '%%';
				});
				progressBar.addEventListener('click', seekFromEvent);
				volumeBar.addEventListener('click', volumeFromEvent);
				audio.addEventListener('play', setPlayState);
				audio.addEventListener('pause', setPlayState);
				audio.addEventListener('ended', () => loadSong(currentIndex + 1, true));
				audio.addEventListener('timeupdate', () => {
					currentTime.textContent = formatTime(audio.currentTime);
					if (Number.isFinite(audio.duration)) progressFill.style.width = ((audio.currentTime / audio.duration) * 100) + '%%';
				});
				audio.addEventListener('loadedmetadata', () => {
					if (Number.isFinite(audio.duration)) durationTime.textContent = formatTime(audio.duration);
				});
				audio.volume = 0.8;
				volumeFill.style.width = '80%%';
				loadSong(currentIndex, false);
				""".formatted(songs, selectedIndex);
	}

	private String renderCanciones(List<Cancion> canciones) {
		if (canciones.isEmpty()) {
			return "<p>No hay canciones para mostrar.</p>";
		}
		StringBuilder html = new StringBuilder();
		for (Cancion cancion : canciones) {
			html.append("""
					<article class="card">
						<img class="cover" src="%s" alt="Portada de %s">
						<div class="content">
							<div class="row"><h2>%s</h2><span class="duration">%s</span></div>
							<div class="meta">%s / %s / %s / %s</div>
							<p class="description">%s</p>
							<audio controls preload="none" src="%s"></audio>
						</div>
					</article>
					""".formatted(
					html(cancion.getImagen()),
					html(cancion.getNombre()),
					html(cancion.getNombre()),
					html(cancion.getDuracion()),
					html(cancion.getActor()),
					html(cancion.getGenero()),
					html(cancion.getAlbum()),
					val(cancion.getAnio()),
					html(cancion.getDescripcion()),
					html(audioSrc(cancion))
			));
		}
		return html.toString();
	}

	private String tablaCanciones(List<Cancion> canciones) {
		StringBuilder filas = new StringBuilder();
		for (Cancion cancion : canciones) {
			filas.append("""
					<tr>
						<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>
						<td>
							<a class="mini" href="/admin?editar=%s" title="Modificar">&#9998;</a>
							<form method="post" action="/admin/canciones/%s/toggle"><button class="mini" title="%s">%s</button></form>
							<form method="post" action="/admin/canciones/%s/delete"><button class="mini danger" title="Eliminar">&#128465;</button></form>
						</td>
					</tr>
					""".formatted(
					cancion.getId(),
					html(cancion.getNombre()),
					html(cancion.getActor()),
					html(cancion.getGenero()),
					Boolean.TRUE.equals(cancion.getActivo()) ? "Activa" : "Inactiva",
					cancion.getId(),
					cancion.getId(),
					Boolean.TRUE.equals(cancion.getActivo()) ? "Desactivar" : "Activar",
					Boolean.TRUE.equals(cancion.getActivo()) ? "&#9208;" : "&#9654;",
					cancion.getId()
			));
		}
		return """
				<table>
					<thead><tr><th>ID</th><th>Nombre</th><th>Actor</th><th>Genero</th><th>Estado</th><th>Acciones</th></tr></thead>
					<tbody>%s</tbody>
				</table>
				""".formatted(filas);
	}

	private String tablaUsuarios(List<Usuario> usuarios) {
		StringBuilder filas = new StringBuilder();
		for (Usuario usuario : usuarios) {
			filas.append("""
					<tr>
						<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>
						<td>
							<form method="post" action="/admin/usuarios/%s/toggle"><button class="mini" title="%s">%s</button></form>
							<form method="post" action="/admin/usuarios/%s/delete"><button class="mini danger" title="Eliminar">&#128465;</button></form>
						</td>
					</tr>
					""".formatted(
					usuario.getId(),
					html(usuario.getNombreCompleto()),
					html(usuario.getCorreo()),
					usuario.getRol(),
					html(usuario.getGeneroFavorito()),
					Boolean.TRUE.equals(usuario.getActivo()) ? "Activo" : "Inactivo",
					usuario.getId(),
					Boolean.TRUE.equals(usuario.getActivo()) ? "Desactivar" : "Activar",
					Boolean.TRUE.equals(usuario.getActivo()) ? "&#9208;" : "&#9654;",
					usuario.getId()
			));
		}
		return """
				<table>
					<thead><tr><th>ID</th><th>Nombre</th><th>Correo</th><th>Rol</th><th>Genero</th><th>Estado</th><th>Acciones</th></tr></thead>
					<tbody>%s</tbody>
				</table>
				""".formatted(filas);
	}

	private String opcionesGenero(String seleccionado) {
		List<String> generos = List.of("Pop", "Urbano", "Balada", "Reggae", "Electronica", "Rock", "Regional", "Jazz");
		StringBuilder opciones = new StringBuilder();
		for (String genero : generos) {
			opciones.append("<option value=\"")
					.append(html(genero))
					.append("\"")
					.append(igual(genero, seleccionado) ? " selected" : "")
					.append(">")
					.append(html(genero))
					.append("</option>");
		}
		return opciones.toString();
	}

	private String page(String title, String body) {
		return """
				<!doctype html>
				<html lang="es">
				<head>
					<meta charset="utf-8">
					<meta name="viewport" content="width=device-width, initial-scale=1">
					<title>%s</title>
					<style>
						* { box-sizing: border-box; }
						body { margin: 0; min-height: 100vh; font-family: Arial, Helvetica, sans-serif; color: #f8fafc; background: #101820; }
						a { color: inherit; text-decoration: none; }
						input, select, textarea, button { width: 100%%; border: 0; border-radius: 8px; font: inherit; }
						input, select, textarea { padding: 11px 12px; color: #10202b; background: #edf7f6; }
						textarea { min-height: 82px; resize: vertical; }
						button, .mini, .secondary-button { cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 10px 12px; color: #07131f; background: #7dd3fc; font-weight: 700; }
						.secondary-button { width: 100%%; margin-top: 12px; border-radius: 8px; color: #f8fafc; background: #263545; }
						.btn-icon { line-height: 1; font-weight: 900; }
						h1 { margin: 0; font-size: 44px; line-height: 1.05; letter-spacing: 0; }
						h2 { margin: 0 0 14px; font-size: 24px; letter-spacing: 0; }
						p { color: #d6e4e5; line-height: 1.5; }
						table { width: 100%%; border-collapse: collapse; background: #17212b; }
						th, td { padding: 12px; border-bottom: 1px solid rgba(255,255,255,0.12); text-align: left; vertical-align: top; }
						th { color: #a7f3d0; }
						td form { display: inline-block; margin: 0 4px 4px 0; }
						.center { min-height: 100vh; display: grid; place-items: center; padding: 24px; }
						.login-card, .panel { width: min(100%%, 560px); padding: 28px; border: 1px solid rgba(255,255,255,0.14); border-radius: 8px; background: #17212b; }
						.shell { width: min(1180px, calc(100%% - 32px)); margin: 0 auto; padding: 32px 0 56px; }
						.topbar { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 18px 24px; background: #0b1218; border-bottom: 1px solid rgba(255,255,255,0.12); }
						.navlinks { display: flex; flex-wrap: wrap; gap: 10px; }
						.navlinks a { padding: 8px 11px; border-radius: 8px; background: #1e2d37; }
						.stack { display: grid; gap: 14px; margin-top: 18px; }
						.hint { margin-top: 18px; color: #b7c7ca; line-height: 1.55; }
						.error, .danger { color: #fecaca; }
						.label { width: fit-content; margin: 0 0 14px; padding: 7px 12px; border-radius: 8px; color: #0f172a; background: #a7f3d0; font-weight: 700; }
						.section { margin-bottom: 28px; }
						.narrow { max-width: 700px; }
						.two-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-bottom: 28px; }
						.two-columns .panel { width: 100%%; }
						.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 18px; margin-top: 18px; }
						.card { overflow: hidden; border: 1px solid rgba(255,255,255,0.14); border-radius: 8px; background: #17212b; }
						.cover { width: 100%%; aspect-ratio: 16 / 10; object-fit: cover; display: block; background: #243241; }
						.content { padding: 18px; }
						.row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
						.duration { flex: 0 0 auto; padding: 5px 9px; border-radius: 8px; color: #102a43; background: #a7f3d0; font-size: 14px; font-weight: 700; }
						.meta { margin-top: 10px; color: #b7c7ca; font-size: 15px; }
						.description { min-height: 48px; }
						audio { width: 100%%; margin-top: 14px; }
						.search { display: grid; grid-template-columns: 1fr 150px; gap: 12px; margin: 18px 0; }
						.table-wrap { overflow-x: auto; border-radius: 8px; }
						.mini { display: inline-block; width: auto; border-radius: 8px; font-size: 14px; }
						@media (max-width: 780px) { .two-columns, .search { grid-template-columns: 1fr; } h1 { font-size: 34px; } .topbar { align-items: flex-start; flex-direction: column; } }
						.music-app { height: 100vh; overflow: hidden; display: grid; grid-template-columns: 386px minmax(560px, 1fr) 526px; grid-template-rows: 70px 1fr 112px; gap: 8px; padding: 0 8px; color: #f7f7f7; background: #000; }
						.music-top { grid-column: 1 / 4; display: grid; grid-template-columns: 140px 70px minmax(360px, 594px) 1fr; align-items: center; gap: 10px; padding: 8px 14px 2px; background: #080a09; }
						.window-dots { display: flex; gap: 16px; color: #bbb; }
						.window-dots span { width: 4px; height: 4px; border-radius: 50%%; background: #d7d7d7; box-shadow: 12px 0 #d7d7d7, 24px 0 #d7d7d7; }
						.round-icon { width: 50px; height: 50px; display: grid; place-items: center; border-radius: 50%%; background: #24251f; font-size: 15px; color: #f6f0d8; font-weight: 900; }
						.music-search { height: 58px; display: grid; grid-template-columns: 46px 1fr 50px; align-items: center; padding: 0 14px; border-radius: 28px; background: #24251f; color: #d9d0b4; border: 1px solid #383629; }
						.music-search span { font-size: 31px; color: #d9d9d9; }
						.music-search input { height: 100%%; padding: 0; color: #f4f4f4; background: transparent; font-size: 20px; outline: 0; }
						.music-search button { width: 44px; height: 44px; padding: 0; color: #bdbdbd; background: transparent; border-left: 1px solid #777; border-radius: 0; }
						.music-actions { justify-self: end; display: flex; align-items: center; gap: 14px; color: #dedede; font-size: 21px; }
						.top-action { min-width: 44px; display: grid; place-items: center; padding: 9px 12px; border-radius: 8px; color: #e8dfbf; background: #273025; font-size: 20px; font-weight: 900; }
						.avatar, .profile-avatar { display: grid; place-items: center; border-radius: 50%%; color: #11130f; background: linear-gradient(135deg, #f5d76e, #4ade80); font-weight: 900; }
						.avatar { width: 48px; height: 48px; }
						.library-panel, .user-main, .now-panel { min-height: 0; overflow: hidden; border-radius: 8px; background: #141713; border: 1px solid #24281f; }
						.library-panel { grid-column: 1; grid-row: 2; padding: 20px; }
						.library-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
						.library-head h2 { margin: 0; font-size: 21px; }
						.library-head div { display: flex; gap: 10px; color: #b7b7b7; font-size: 15px; font-weight: 800; }
						.library-head a, .library-tools a { color: #e8dfbf; }
						.chips { display: flex; gap: 8px; margin-bottom: 20px; }
						.chips a { padding: 10px 15px; border-radius: 22px; background: #273025; color: #f6f0d8; font-weight: 700; }
						.library-tools { display: flex; justify-content: space-between; margin: 8px 0 14px; color: #bdbdbd; font-size: 18px; }
						.library-item { height: 80px; display: grid; grid-template-columns: 60px 1fr; align-items: center; gap: 14px; padding: 6px 10px; border-radius: 8px; color: #dedede; }
						.library-item.active, .library-item:hover { background: #2b3428; }
						.library-item img, .saved-icon, .liked-icon { width: 60px; height: 60px; border-radius: 6px; object-fit: cover; }
						.saved-icon { display: grid; place-items: center; color: #0e1510; background: #f5d76e; font-size: 24px; font-weight: 900; }
						.liked-icon { display: grid; place-items: center; color: #0e1510; background: linear-gradient(135deg, #4ade80, #fca5a5); font-size: 24px; font-weight: 900; }
						.library-item strong { display: block; color: #fff; font-size: 18px; line-height: 1.2; }
						.library-item span { color: #b7b7b7; font-size: 16px; }
						.user-main { grid-column: 2; grid-row: 2; background: linear-gradient(180deg, #385238 0, #24301f 300px, #141713 520px); }
						.main-scroll, .now-scroll { height: 100%%; overflow: auto; scrollbar-color: #555 transparent; }
						.hero { min-height: 310px; display: flex; align-items: end; gap: 24px; padding: 68px 24px 30px; }
						.song-hero { background: linear-gradient(180deg, #5f7b38 0, #35512e 54%%, rgba(20,23,19,0.35) 100%%); }
						.library-hero { background: linear-gradient(135deg, #2f5f47 0, #7a8f3c 52%%, #1e2b1d 100%%); }
						.profile-hero { min-height: 270px; }
						.hero-cover { width: 216px; height: 216px; border-radius: 6px; object-fit: cover; box-shadow: 0 14px 32px rgba(0,0,0,0.45); }
						.hero-type { margin: 0 0 12px; color: #fff; font-weight: 800; }
						.hero h1 { max-width: 900px; font-size: 64px; font-weight: 900; letter-spacing: 0; text-wrap: balance; }
						.profile-avatar { width: 180px; height: 180px; font-size: 58px; box-shadow: 0 14px 32px rgba(0,0,0,0.45); }
						.list-surface { min-height: 520px; padding: 24px; background: linear-gradient(180deg, rgba(29,35,24,0.76), #141713 190px); }
						.toolbar { height: 62px; display: flex; align-items: center; gap: 24px; margin-bottom: 20px; color: #bfbfbf; font-size: 32px; }
						.play-big { width: 72px; height: 72px; padding: 0; border-radius: 50%%; color: #11130f; background: #f5d76e; font-size: 30px; font-weight: 900; }
						.ghost { color: #e8dfbf; }
						.action-button { width: auto; min-width: 44px; padding: 10px 14px; border-radius: 8px; background: #273025; color: #f6f0d8; font-size: 18px; }
						.toolbar-right { margin-left: auto; font-size: 16px; }
						.inline-search { display: grid; grid-template-columns: 1fr 140px; gap: 12px; margin-bottom: 20px; }
						.inline-search input { color: #fff; background: #273025; }
						.inline-search button { color: #11130f; background: #f5d76e; }
						.song-table { display: grid; gap: 3px; }
						.table-header, .song-row { display: grid; grid-template-columns: 42px minmax(260px, 1.5fr) minmax(180px, 1fr) 150px 70px; align-items: center; gap: 12px; padding: 10px 18px; color: #c8c1a8; border-radius: 6px; }
						.table-header { border-bottom: 1px solid #2d2d2d; font-size: 16px; }
						.song-row { min-height: 68px; }
						.song-row:hover, .song-row.playing { background: #2b3428; }
						.song-row.playing .row-index, .song-row.playing strong { color: #f5d76e; }
						.row-title { display: grid; grid-template-columns: 50px 1fr; gap: 14px; align-items: center; min-width: 0; }
						.row-title img { width: 50px; height: 50px; border-radius: 4px; object-fit: cover; }
						.row-title strong, .row-title em { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
						.row-title strong { color: #fff; font-size: 18px; font-style: normal; }
						.row-title em { color: #b7b7b7; font-style: normal; }
						.empty-state { padding: 22px; border-radius: 8px; background: #202020; }
						.now-panel { grid-column: 3; grid-row: 2; padding: 24px 20px; }
						.now-panel h2 { font-size: 22px; }
						.now-cover { width: 100%%; aspect-ratio: 1 / 1; border-radius: 6px; object-fit: cover; margin: 18px 0 24px; }
						.now-title { display: flex; justify-content: space-between; gap: 18px; align-items: start; }
						.now-title h3 { margin: 0; font-size: 32px; }
						.now-title p { margin: 6px 0 0; font-size: 19px; }
						.now-title .icon-button { width: 34px; height: 34px; padding: 0; border-radius: 50%%; font-size: 18px; }
						.now-card { margin: 26px 0; padding: 18px; border-radius: 8px; background: #24251f; }
						.now-card h3 { margin-top: 0; }
						.video-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
						.video-grid div { height: 90px; border-radius: 6px; background: linear-gradient(135deg, #334155, #14532d); }
						.player-bar { grid-column: 1 / 4; grid-row: 3; display: grid; grid-template-columns: 360px 1fr 360px; align-items: center; gap: 18px; padding: 14px 20px; background: #080a09; border-top: 1px solid #24281f; }
						.mini-track { display: grid; grid-template-columns: 64px 1fr 34px; align-items: center; gap: 14px; }
						.mini-track img { width: 64px; height: 64px; border-radius: 4px; object-fit: cover; }
						.mini-track strong, .mini-track span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
						.mini-track span { color: #b7b7b7; }
						.add { color: #b7b7b7; font-size: 22px; }
						.player-center { display: grid; gap: 10px; justify-items: center; }
						.controls { display: flex; align-items: center; gap: 14px; color: #c8c1a8; }
						.control-button, .icon-button { width: auto; min-width: 38px; padding: 9px 11px; border-radius: 8px; color: #f6f0d8; background: #273025; font-size: 18px; font-weight: 900; }
						.control-button:hover, .control-button.active, .icon-button.active { color: #11130f; background: #f5d76e; }
						.main-control { width: 58px; height: 42px; padding: 0; color: #11130f; background: #f5d76e; }
						.progress { width: min(720px, 100%%); display: grid; grid-template-columns: 48px 1fr 48px; align-items: center; gap: 10px; color: #d0d0d0; }
						.progress button, .volume { height: 6px; padding: 0; border-radius: 999px; background: #3b4034; }
						.progress i { display: block; width: 0%%; height: 100%%; border-radius: inherit; background: #f5d76e; }
						.player-tools { display: flex; justify-content: end; align-items: center; gap: 14px; color: #b7b7b7; }
						.volume { width: 120px; }
						.volume i { display: block; width: 78%%; height: 100%%; border-radius: inherit; background: #4ade80; }
						.tool-link { color: #e8dfbf; font-weight: 800; }
						.profile-form { max-width: 720px; margin: 0 auto; }
						.profile-form select { background: #2a2a2a; color: #fff; }
						.logout-link { display: inline-block; margin-top: 20px; color: #1ed760; font-weight: 800; }
						@media (max-width: 1320px) { .music-app { grid-template-columns: 320px minmax(0, 1fr); } .now-panel { display: none; } .music-top, .player-bar { grid-column: 1 / 3; } .user-main { grid-column: 2; } }
						@media (max-width: 860px) { .music-app { height: auto; min-height: 100vh; overflow: visible; display: block; padding: 0; } .music-top, .library-panel, .user-main, .player-bar { border-radius: 0; } .library-panel, .player-tools { display: none; } .music-top { display: grid; grid-template-columns: 46px 1fr 46px; padding: 10px; } .window-dots { display: none; } .music-search { height: 48px; grid-template-columns: 36px 1fr; } .music-search button { display: none; } .hero { min-height: auto; display: block; padding: 28px 16px; } .hero-cover, .profile-avatar { width: 140px; height: 140px; margin-bottom: 18px; } .hero h1 { font-size: 40px; } .table-header { display: none; } .song-row { grid-template-columns: 34px 1fr 52px; } .song-row > span:nth-child(3), .song-row > span:nth-child(4) { display: none; } .player-bar { position: sticky; bottom: 0; grid-template-columns: 1fr; } .mini-track, .player-center { width: 100%%; } }
					</style>
				</head>
				<body>%s</body>
				</html>
				""".formatted(html(title), body);
	}

	private boolean contiene(String value, String texto) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(texto);
	}

	private boolean igual(String a, String b) {
		return a != null && b != null && a.equalsIgnoreCase(b);
	}

	private boolean vacio(String value) {
		return value == null || value.isBlank();
	}

	private String val(Object value) {
		return value == null ? "" : html(String.valueOf(value));
	}

	private String html(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

	private String js(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "")
				.replace("\n", "\\n")
				.replace("</", "<\\/");
	}

	private String url(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
