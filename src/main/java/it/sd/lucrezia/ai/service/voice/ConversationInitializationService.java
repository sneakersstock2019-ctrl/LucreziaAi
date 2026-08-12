package it.sd.lucrezia.ai.service.voice;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.bean.VoiceConversationContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationInitializationService {

    @Value("${voice.elevenlabs.utente-sconosciuto-branch-id}")
    private String utenteSconosciutoBranchId;

    private final UtenteDao utenteDao;
    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;

    public VoiceConversationContext initialize(
            String fromNumber,
            String toNumber,
            String callSid,
            String conversationId,
            String canale
    ) {

        String telefono = normalizePhoneNumber(fromNumber);
        String numeroLucrezia = normalizePhoneNumber(toNumber);
        String normalizedCallSid = safe(callSid);

        if (telefono.isBlank()) {
            throw new IllegalArgumentException(
                    "Numero chiamante non presente"
            );
        }

        Utente utente =
                utenteDao.findCondominoByTelefono(telefono);

        /*
         * ============================================================
         * NUMERO NON CENSITO
         * ============================================================
         */
        if (utente == null) {

        	//TODO
        	if(telefono.endsWith("3492123304")) {
        		return initializeUtenteSconosciuto(
        				telefono,
        				numeroLucrezia,
        				normalizedCallSid,
        				conversationId,
        				canale
        				);
        	} else {
                throw new IllegalStateException(
                        "Nessun condomino associato al numero "
                                + telefono
                );
        	}
        }

        /*
         * ============================================================
         * UTENTE CONOSCIUTO
         * ============================================================
         */

        int ticketAperti =
                ticketDao.countTicketApertiByUtente(
                        utente.getId()
                );

        Long idTelefonata =
                telefonataDao.insertTelefonata(
                        normalizedCallSid,
                        telefono,
                        utente.getId(),
                        utente.getIdCondominio()
                );

        if (idTelefonata == null) {
            throw new IllegalStateException(
                    "Impossibile registrare la telefonata "
                            + normalizedCallSid
            );
        }

        if (conversationId != null
                && !conversationId.isBlank()) {

            telefonataDao.updateElevenLabsConversationId(
                    idTelefonata,
                    conversationId,
                    normalizedCallSid
            );
        }

        String firstMessage =
                buildFirstMessage(
                        utente,
                        ticketAperti
                );

        Map<String, Object> dynamicVariables =
                new LinkedHashMap<>();

        dynamicVariables.put(
                "call_sid",
                normalizedCallSid
        );

        dynamicVariables.put(
                "conversation_id",
                safe(conversationId)
        );

        dynamicVariables.put(
                "id_telefonata",
                String.valueOf(idTelefonata)
        );

        dynamicVariables.put(
                "id_utente",
                String.valueOf(utente.getId())
        );

        dynamicVariables.put(
                "id_condominio",
                String.valueOf(utente.getIdCondominio())
        );

        dynamicVariables.put(
                "nome",
                safe(utente.getNome())
        );

        dynamicVariables.put(
                "telefono",
                telefono
        );

        dynamicVariables.put(
                "numero_lucrezia",
                numeroLucrezia
        );

        dynamicVariables.put(
                "condominio",
                safe(utente.getNomeCondominio())
        );

        dynamicVariables.put(
                "codice_fiscale_condominio",
                safe(utente.getCodiceFiscaleCondominio())
        );

        dynamicVariables.put(
                "ticket_aperti",
                ticketAperti
        );

        dynamicVariables.put(
                "first_message",
                firstMessage
        );

        dynamicVariables.put(
                "canale",
                safe(canale)
        );

        dynamicVariables.put(
                "branch_condominio",
                buildBranchName(utente)
        );

        dynamicVariables.put(
                "branch_id",
                safe(utente.getElevenlabsBranchId())
        );

        dynamicVariables.put(
                "utente_riconosciuto",
                true
        );

        aggiungiVariabiliDataOra(dynamicVariables);

        CallLogger.info(
                normalizedCallSid,
                "CONVERSATION INIT"
                        + " - canale=" + canale
                        + " idTelefonata=" + idTelefonata
                        + " idUtente=" + utente.getId()
                        + " idCondominio=" + utente.getIdCondominio()
                        + " branchId=" + utente.getElevenlabsBranchId()
        );

        return new VoiceConversationContext(
                idTelefonata,
                utente,
                ticketAperti,
                utente.getElevenlabsBranchId(),
                firstMessage,
                dynamicVariables
        );
    }

    /*
     * ================================================================
     * UTENTE SCONOSCIUTO
     * ================================================================
     */

    private VoiceConversationContext initializeUtenteSconosciuto(
            String telefono,
            String numeroLucrezia,
            String callSid,
            String conversationId,
            String canale
    ) {

        /*
         * Registriamo comunque la telefonata.
         *
         * id_utente e id_condominio sono NULL perché
         * non abbiamo ancora identificato la persona.
         */
        Long idTelefonata =
                telefonataDao.insertTelefonata(
                        callSid,
                        telefono,
                        null,
                        null
                );

        if (idTelefonata == null) {
            throw new IllegalStateException(
                    "Impossibile registrare la telefonata "
                            + callSid
            );
        }

        if (conversationId != null
                && !conversationId.isBlank()) {

            telefonataDao.updateElevenLabsConversationId(
                    idTelefonata,
                    conversationId,
                    callSid
            );
        }

        String firstMessage =
                buildFirstMessageUtenteSconosciuto();

        Map<String, Object> dynamicVariables =
                new LinkedHashMap<>();

        dynamicVariables.put(
                "call_sid",
                callSid
        );

        dynamicVariables.put(
                "conversation_id",
                safe(conversationId)
        );

        dynamicVariables.put(
                "id_telefonata",
                String.valueOf(idTelefonata)
        );

        /*
         * Numero dal quale sta chiamando la nuova persona.
         */
        dynamicVariables.put(
                "telefono_sconosciuto",
                telefono
        );

        dynamicVariables.put(
                "telefono",
                telefono
        );

        dynamicVariables.put(
                "numero_lucrezia",
                numeroLucrezia
        );

        dynamicVariables.put(
                "canale",
                safe(canale)
        );

        dynamicVariables.put(
                "first_message",
                firstMessage
        );

        dynamicVariables.put(
                "branch_id",
                utenteSconosciutoBranchId
        );

        dynamicVariables.put(
                "branch_condominio",
                "UTENTE_SCONOSCIUTO"
        );

        dynamicVariables.put(
                "utente_riconosciuto",
                false
        );

        /*
         * Partiamo da zero.
         * In seguito potremo passarla ai tool.
         */
        dynamicVariables.put(
                "tentativi_identificazione",
                0
        );

        aggiungiVariabiliDataOra(dynamicVariables);

        CallLogger.info(
                callSid,
                "CONVERSATION INIT UTENTE SCONOSCIUTO"
                        + " - canale=" + canale
                        + " idTelefonata=" + idTelefonata
                        + " telefono=" + telefono
                        + " branchId=" + utenteSconosciutoBranchId
        );

        return new VoiceConversationContext(
                idTelefonata,
                null,
                0,
                utenteSconosciutoBranchId,
                firstMessage,
                dynamicVariables
        );
    }

    private String buildFirstMessageUtenteSconosciuto() {

        return buildSalutoOrario()
                + ", sono Lucrezia, "
                + "l'assistente dell'amministratore "
                + "per gli interventi condominiali. "
                + "Il numero dal quale stai chiamando non risulta ancora registrato, "
                + "ma posso comunque aiutarti. "
                + "Prima di tutto, puoi dirmi il tuo nome e cognome?";
    }

    /*
     * ================================================================
     * FIRST MESSAGE UTENTE CONOSCIUTO
     * ================================================================
     */

    private String buildFirstMessage(
            Utente utente,
            int ticketAperti
    ) {

        String nome = safe(utente.getNome());
        String saluto = buildSalutoOrario();

        if (ticketAperti == 1) {

            return saluto + " " + nome
                    + ", sono Lucrezia. "
                    + "Vedo che hai una segnalazione aperta. "
                    + "Vuoi conoscerne lo stato oppure "
                    + "aprire una nuova segnalazione?";
        }

        if (ticketAperti > 1) {

            return saluto + " " + nome
                    + ", sono Lucrezia. "
                    + "Vedo che hai " + ticketAperti
                    + " segnalazioni aperte. "
                    + "Vuoi conoscere lo stato di una segnalazione "
                    + "oppure aprirne una nuova?";
        }

        return saluto + " " + nome
                + ", sono Lucrezia, "
                + "l'assistente dell'amministratore "
                + "per gli interventi condominiali. "
                + "Come posso aiutarti?";
    }

    /*
     * ================================================================
     * DATA / ORA
     * ================================================================
     */

    private void aggiungiVariabiliDataOra(
            Map<String, Object> dynamicVariables) {

        LocalDateTime dataOraChiamata =
                LocalDateTime.now(
                        ZoneId.of("Europe/Rome")
                );

        dynamicVariables.put(
                "orario_chiamata",
                dataOraChiamata.format(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"
                        )
                )
        );

        dynamicVariables.put(
                "saluto_orario",
                buildSalutoOrario()
        );
    }

    private String buildSalutoOrario() {

        int ora =
                LocalDateTime.now(
                        ZoneId.of("Europe/Rome")
                ).getHour();

        if (ora >= 5 && ora < 18) {
            return "Buongiorno";
        }

        return "Buonasera";
    }

    /*
     * ================================================================
     * UTILITY
     * ================================================================
     */

    private String buildBranchName(Utente utente) {

        String codiceFiscale =
                safe(
                        utente.getCodiceFiscaleCondominio()
                );

        String nomeCondominio =
                safe(
                        utente.getNomeCondominio()
                );

        return codiceFiscale
                + " - "
                + nomeCondominio;
    }

    private String normalizePhoneNumber(String phone) {

        if (phone == null) {
            return "";
        }

        phone = phone.replaceAll(
                "[^0-9]",
                ""
        );

        if (phone.length() > 10) {

            phone = phone.substring(
                    phone.length() - 10
            );
        }

        return phone;
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}