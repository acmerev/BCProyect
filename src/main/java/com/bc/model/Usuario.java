package com.bc.model;

import lombok.Data;


@Data
public class Usuario {

    private String nombre;
    private String apellido;
    private int edad;
    private String email;

	public Usuario() {
	}
	public Usuario(String nombre, String apellido, int edad, String email) {
		this.nombre = nombre;
		this.apellido = apellido;
		this.edad = edad;
		this.email = email;
	}

}
