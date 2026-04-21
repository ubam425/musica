package com.musica.musica;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CorreoService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CorreoService.class);

	private final JavaMailSender mailSender;
	private final String remitente;

	public CorreoService(JavaMailSender mailSender, @Value("${musicubam.mail.from}") String remitente) {
		this.mailSender = mailSender;
		this.remitente = remitente;
	}

	public void enviarCredenciales(Usuario usuario) {
		enviar(
				usuario.getCorreo(),
				"Acceso a MusicUbam",
				plantilla(
						"Tu cuenta de MusicUbam",
						"Estos son tus datos para entrar a la plataforma.",
						usuario
				)
		);
	}

	public void enviarRecuperacion(Usuario usuario) {
		enviar(
				usuario.getCorreo(),
				"Recuperacion de acceso a MusicUbam",
				plantilla(
						"Recuperacion de contrasena",
						"Recibimos una solicitud para recuperar tu acceso. Estos son tus datos actuales.",
						usuario
				)
		);
	}

	private void enviar(String destino, String asunto, String html) {
		try {
			MimeMessage mensaje = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
			helper.setFrom(remitente);
			helper.setTo(destino);
			helper.setSubject(asunto);
			helper.setText(html, true);
			mailSender.send(mensaje);
		} catch (MessagingException | MailException ex) {
			String diagnostico = diagnosticar(ex);
			LOGGER.error("No se pudo enviar correo a {}. {}", destino, diagnostico, ex);
			throw new IllegalStateException(diagnostico, ex);
		}
	}

	private String diagnosticar(Exception ex) {
		Throwable causa = ex;
		while (causa.getCause() != null) {
			causa = causa.getCause();
		}
		String nombre = causa.getClass().getSimpleName();
		String mensaje = causa.getMessage() == null ? "" : causa.getMessage();
		String texto = (nombre + " " + mensaje).toLowerCase();
		if (texto.contains("authentication") || texto.contains("535")) {
			return "Hostinger rechazo el usuario o la contrasena SMTP. Verifica el buzon admin@chat.portafolioestudiantil.com y su contrasena.";
		}
		if (texto.contains("connection") || texto.contains("timeout") || texto.contains("couldn't connect")) {
			return "No se pudo conectar con smtp.hostinger.com:465. Revisa internet, puerto 465 y SSL.";
		}
		if (texto.contains("ssl") || texto.contains("handshake")) {
			return "Fallo SSL con Hostinger. El servidor debe usar smtp.hostinger.com, puerto 465 y SSL.";
		}
		return "No se pudo enviar el correo. Detalle: " + nombre + (mensaje.isBlank() ? "" : " - " + mensaje);
	}

	private String plantilla(String titulo, String texto, Usuario usuario) {
		return """
				<!doctype html>
				<html lang="es">
				<head>
					<meta charset="utf-8">
				</head>
				<body style="margin:0;background:#0e141b;font-family:Arial,Helvetica,sans-serif;color:#17202a;">
					<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#0e141b;padding:28px 12px;">
						<tr>
							<td align="center">
								<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border-radius:8px;overflow:hidden;">
									<tr>
										<td style="background:#163b35;padding:28px;color:#ffffff;">
											<div style="font-size:14px;letter-spacing:.08em;text-transform:uppercase;color:#a7f3d0;font-weight:700;">MusicUbam</div>
											<h1 style="margin:10px 0 0;font-size:30px;line-height:1.1;">%s</h1>
										</td>
									</tr>
									<tr>
										<td style="padding:28px;">
											<p style="margin:0 0 18px;font-size:17px;line-height:1.5;color:#425466;">%s</p>
											<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;background:#f4fbf8;border-radius:8px;overflow:hidden;">
												<tr>
													<td style="padding:14px 16px;border-bottom:1px solid #d9ede5;color:#64748b;font-weight:700;">Nombre</td>
													<td style="padding:14px 16px;border-bottom:1px solid #d9ede5;color:#111827;">%s</td>
												</tr>
												<tr>
													<td style="padding:14px 16px;border-bottom:1px solid #d9ede5;color:#64748b;font-weight:700;">Correo</td>
													<td style="padding:14px 16px;border-bottom:1px solid #d9ede5;color:#111827;">%s</td>
												</tr>
												<tr>
													<td style="padding:14px 16px;color:#64748b;font-weight:700;">Contrasena</td>
													<td style="padding:14px 16px;color:#111827;font-size:20px;font-weight:800;">%s</td>
												</tr>
											</table>
											<p style="margin:22px 0 0;color:#64748b;font-size:14px;line-height:1.5;">Guarda estos datos en un lugar seguro. Puedes iniciar sesion en MusicUbam con tu correo y contrasena.</p>
										</td>
									</tr>
								</table>
							</td>
						</tr>
					</table>
				</body>
				</html>
				""".formatted(
				escapar(titulo),
				escapar(texto),
				escapar(usuario.getNombreCompleto()),
				escapar(usuario.getCorreo()),
				escapar(usuario.getContrasena())
		);
	}

	private String escapar(String texto) {
		if (texto == null) {
			return "";
		}
		return texto
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
