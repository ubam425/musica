package com.musica.musica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nombres;
	private String apellidoP;
	private String apellidoM;

	@Column(unique = true, nullable = false)
	private String correo;

	@Column(nullable = false)
	private String contrasena;

	@Enumerated(EnumType.STRING)
	private Rol rol = Rol.USUARIO;

	private String generoFavorito;
	private Boolean activo = true;

	public Usuario() {
	}

	public Usuario(
			String nombres,
			String apellidoP,
			String apellidoM,
			String correo,
			String contrasena,
			Rol rol,
			String generoFavorito
	) {
		this.nombres = nombres;
		this.apellidoP = apellidoP;
		this.apellidoM = apellidoM;
		this.correo = correo;
		this.contrasena = contrasena;
		this.rol = rol;
		this.generoFavorito = generoFavorito;
		this.activo = true;
	}

	public Long getId() {
		return id;
	}

	public String getNombres() {
		return nombres;
	}

	public void setNombres(String nombres) {
		this.nombres = nombres;
	}

	public String getApellidoP() {
		return apellidoP;
	}

	public void setApellidoP(String apellidoP) {
		this.apellidoP = apellidoP;
	}

	public String getApellidoM() {
		return apellidoM;
	}

	public void setApellidoM(String apellidoM) {
		this.apellidoM = apellidoM;
	}

	public String getCorreo() {
		return correo;
	}

	public void setCorreo(String correo) {
		this.correo = correo;
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

	public Rol getRol() {
		return rol;
	}

	public void setRol(Rol rol) {
		this.rol = rol;
	}

	public String getGeneroFavorito() {
		return generoFavorito;
	}

	public void setGeneroFavorito(String generoFavorito) {
		this.generoFavorito = generoFavorito;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public String getNombreCompleto() {
		return String.join(" ", nombres, apellidoP, apellidoM).trim();
	}
}
