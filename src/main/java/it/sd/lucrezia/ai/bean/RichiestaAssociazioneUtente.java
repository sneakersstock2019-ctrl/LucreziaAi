package it.sd.lucrezia.ai.bean;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RichiestaAssociazioneUtente {

    private Long id;

    private String telefonoNuovo;
    private String nomeNuovo;
    private String cognomeNuovo;

    private Long idUtenteRegistrato;
    private Long idCondominio;
    private Long idTelefonata;

    private String stato;

    private LocalDateTime dataCreazione;
    private LocalDateTime dataRisposta;
}