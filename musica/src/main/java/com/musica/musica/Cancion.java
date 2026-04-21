package com.musica.musica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class Cancion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 600)
	private String imagen;

	private String nombre;
	private String actor;
	private String genero;
	private String duracion;

	@Column(length = 600)
	private String musica;

	private String audioNombre;
	private String audioTipo;

	@Lob
	@Column(columnDefinition = "LONGBLOB")
	private byte[] audioDatos;

	private String album;
	private Integer anio;
	private Boolean activo = true;

	@Column(length = 600)
	private String descripcion;

	public Cancion() {
	}

	public Cancion(
			Long id,
			String imagen,
			String nombre,
			String actor,
			String genero,
			String duracion,
			String musica,
			String album,
			Integer anio,
			String descripcion
	) {
		this.id = id;
		this.imagen = imagen;
		this.nombre = nombre;
		this.actor = actor;
		this.genero = genero;
		this.duracion = duracion;
		this.musica = musica;
		this.album = album;
		this.anio = anio;
		this.descripcion = descripcion;
		this.activo = true;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getImagen() {
		return imagen;
	}

	public void setImagen(String imagen) {
		this.imagen = imagen;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getActor() {
		return actor;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

	public String getGenero() {
		return genero;
	}

	public void setGenero(String genero) {
		this.genero = genero;
	}

	public String getDuracion() {
		return duracion;
	}

	public void setDuracion(String duracion) {
		this.duracion = duracion;
	}

	public String getMusica() {
		return musica;
	}

	public void setMusica(String musica) {
		this.musica = musica;
	}

	public String getAudioNombre() {
		return audioNombre;
	}

	public void setAudioNombre(String audioNombre) {
		this.audioNombre = audioNombre;
	}

	public String getAudioTipo() {
		return audioTipo;
	}

	public void setAudioTipo(String audioTipo) {
		this.audioTipo = audioTipo;
	}

	public byte[] getAudioDatos() {
		return audioDatos;
	}

	public void setAudioDatos(byte[] audioDatos) {
		this.audioDatos = audioDatos;
	}

	public boolean tieneAudioEnBaseDatos() {
		return audioDatos != null && audioDatos.length > 0;
	}

	public String getAlbum() {
		return album;
	}

	public void setAlbum(String album) {
		this.album = album;
	}

	public Integer getAnio() {
		return anio;
	}

	public void setAnio(Integer anio) {
		this.anio = anio;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}
}
