package it.sd.lucrezia.ai.service.voice;

import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.CreatePendingTicketRequest;
import it.sd.lucrezia.ai.bean.CreatePendingTicketResponse;
import it.sd.lucrezia.ai.bean.RichiestaAssociazioneUtente;
import it.sd.lucrezia.ai.dao.RichiestaAssociazioneUtenteDao;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnknownUserTicketService {

    private final RichiestaAssociazioneUtenteDao richiestaDao;
    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;

    public CreatePendingTicketResponse createPendingTicket(
            CreatePendingTicketRequest request) {

        CreatePendingTicketResponse response =
                new CreatePendingTicketResponse();

        /*
         * ============================================================
         * VALIDAZIONE REQUEST
         * ============================================================
         */

        if (request == null
                || request.getIdTelefonata() == null) {

            response.setSuccess(false);

            response.setMessage(
                    "Non è stato possibile identificare "
                            + "la telefonata corrente."
            );

            response.setNextAction(
                    "ERROR"
            );

            return response;
        }

        if (isBlank(request.getDescrizione())) {

            response.setSuccess(false);

            response.setMessage(
                    "Manca la descrizione della segnalazione."
            );

            response.setNextAction(
                    "MISSING_INFORMATION"
            );

            return response;
        }

        if (isBlank(request.getArea())) {

            response.setSuccess(false);

            response.setMessage(
                    "Manca il luogo o l'area interessata "
                            + "dalla segnalazione."
            );

            response.setNextAction(
                    "MISSING_INFORMATION"
            );

            return response;
        }

        /*
         * ============================================================
         * RECUPERO RICHIESTA ASSOCIAZIONE
         * ============================================================
         */

        RichiestaAssociazioneUtente richiesta =
                richiestaDao.findByIdTelefonata(
                        request.getIdTelefonata()
                );

        if (richiesta == null) {

            response.setSuccess(false);

            response.setMessage(
                    "Non è stata trovata una richiesta "
                            + "di associazione collegata "
                            + "alla telefonata."
            );

            response.setNextAction(
                    "APPROVAL_REQUEST_NOT_FOUND"
            );

            return response;
        }

        /*
         * ============================================================
         * NORMALIZZAZIONE DATI TICKET
         * ============================================================
         */

        String categoria =
                normalizeCategoria(
                        request.getCategoria()
                );

        String priorita =
                normalizePriorita(
                        request.getPriorita()
                );

        String area =
                normalizeArea(
                        request.getArea()
                );

        /*
         * ============================================================
         * DETERMINAZIONE FLUSSO
         * ============================================================
         */

        String statoRichiesta =
                richiesta.getStato();

        boolean verificaAdmin =
                "DA_VERIFICARE_ADMIN"
                        .equalsIgnoreCase(
                                statoRichiesta
                        );

        /*
         * Se non siamo nel flusso ADMIN,
         * il condominio deve essere già conosciuto.
         */
        if (!verificaAdmin
                && richiesta.getIdCondominio() == null) {

            response.setSuccess(false);

            response.setMessage(
                    "La richiesta di associazione "
                            + "non contiene un condominio valido."
            );

            response.setNextAction(
                    "ERROR"
            );

            return response;
        }

        /*
         * Stato iniziale del ticket.
         */
        String statoTicket;

        if (verificaAdmin) {

            statoTicket =
                    "IN_ATTESA_VERIFICA_ADMIN";

        } else {

            statoTicket =
                    "IN_ATTESA_APPROVAZIONE";
        }

        /*
         * ============================================================
         * CREAZIONE TICKET
         * ============================================================
         *
         * Per DA_VERIFICARE_ADMIN id_condominio può essere NULL.
         */

        Long idTicket =
                ticketDao.insertPendingTicket(
                        richiesta.getIdCondominio(),
                        richiesta.getId(),
                        categoria,
                        priorita,
                        "TELEFONO",
                        area,
                        request.getDescrizione(),
                        statoTicket
                );

        if (idTicket == null) {

            response.setSuccess(false);

            response.setMessage(
                    "Non è stato possibile registrare "
                            + "la segnalazione."
            );

            response.setNextAction(
                    "ERROR"
            );

            return response;
        }

        /*
         * ============================================================
         * ASSOCIAZIONE TICKET ALLA TELEFONATA
         * ============================================================
         *
         * Fondamentale anche per il ticket DA_VERIFICARE_ADMIN:
         * trascrizione e audio devono rimanere collegati.
         */

        telefonataDao.updateIdTicket(
                request.getIdTelefonata(),
                idTicket
        );

        /*
         * ============================================================
         * RESPONSE - VERIFICA ADMIN
         * ============================================================
         */

        if (verificaAdmin) {

            response.setSuccess(true);
            response.setIdTicket(idTicket);

            response.setStato(
                    "IN_ATTESA_VERIFICA_ADMIN"
            );

            response.setMessage(
                    "La segnalazione è stata registrata. "
                            + "Non è stato possibile identificare "
                            + "con certezza l'utente registrato, "
                            + "quindi la richiesta verrà verificata "
                            + "da un amministratore."
            );

            response.setNextAction(
                    "PENDING_ADMIN_VERIFICATION_CREATED"
            );

            return response;
        }

        /*
         * ============================================================
         * RESPONSE - NORMALE ATTESA APPROVAZIONE
         * ============================================================
         */

        response.setSuccess(true);
        response.setIdTicket(idTicket);

        response.setStato(
                "IN_ATTESA_APPROVAZIONE"
        );

        response.setMessage(
                "La segnalazione è stata registrata. "
                        + "Resterà in attesa finché la richiesta "
                        + "di autorizzazione non sarà approvata."
        );

        response.setNextAction(
                "PENDING_TICKET_CREATED"
        );

        return response;
    }

    private String normalizeCategoria(
            String categoria) {

        if (categoria == null
                || categoria.isBlank()) {

            return "generico";
        }

        categoria =
                categoria.trim().toLowerCase();

        return switch (categoria) {

            case "ascensore" ->
                    "ascensore";

            case "elettricista" ->
                    "elettricista";

            case "idraulico" ->
                    "idraulico";

            case "pulizia" ->
                    "pulizia";

            case "cancello" ->
                    "cancello";

            default ->
                    "generico";
        };
    }

    private String normalizePriorita(
            String priorita) {

        if (priorita == null
                || priorita.isBlank()) {

            return "media";
        }

        return switch (
                priorita.trim().toLowerCase()) {

            case "alta" ->
                    "alta";

            case "bassa" ->
                    "bassa";

            default ->
                    "media";
        };
    }

    private String normalizeArea(
            String area) {

        if (area == null) {
            return "";
        }

        return "privata".equalsIgnoreCase(
                area.trim())
                        ? "privata"
                        : "comune";
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }
}