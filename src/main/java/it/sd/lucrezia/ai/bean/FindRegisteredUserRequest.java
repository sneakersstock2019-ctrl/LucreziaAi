package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class FindRegisteredUserRequest {

    private String telefonoRegistrato;

    private String nomeRegistrato;
    private String cognomeRegistrato;
    private String indirizzoCondominio;
    private String interno;

    private String nomeNuovo;
    private String cognomeNuovo;

    /*
     * Arriva da {{telefono_sconosciuto}}
     */
    private String telefonoNuovo;

    /*
     * Arriva da {{id_telefonata}}
     */
    private Long idTelefonata;
}