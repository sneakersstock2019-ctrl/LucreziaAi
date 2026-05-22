package it.sd.demo.bot.condomini.bean;

import java.time.LocalDateTime;

public class Ticket {

    private Long id;

    private String nome;

    private String telefono;

    private String descrizione;

    private String categoria;

    private String stato;

    private LocalDateTime dataApertura;

    private LocalDateTime dataChiusura;

    // =========================
    // LIFECYCLE
    // =========================
    public void prePersist() {
        this.dataApertura = LocalDateTime.now();
        if (this.stato == null) {
            this.stato = "APERTO";
        }
    }

    // =========================
    // GETTER / SETTER
    // =========================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getStato() {
        return stato;
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public LocalDateTime getDataApertura() {
        return dataApertura;
    }

    public void setDataApertura(LocalDateTime dataApertura) {
        this.dataApertura = dataApertura;
    }

    public LocalDateTime getDataChiusura() {
        return dataChiusura;
    }

    public void setDataChiusura(LocalDateTime dataChiusura) {
        this.dataChiusura = dataChiusura;
    }
}