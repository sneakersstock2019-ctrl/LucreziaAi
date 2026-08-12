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

        RichiestaAssociazioneUtente richiesta =
                richiestaDao.findByIdTelefonata(
                        request.getIdTelefonata()
                );

        if (richiesta == null) {

            response.setSuccess(false);

            response.setMessage(
                    "Non è stata trovata una richiesta "
                    + "di autorizzazione associata alla telefonata."
            );

            response.setNextAction(
                    "APPROVAL_REQUEST_NOT_FOUND"
            );

            return response;
        }

        /*
         * ATTENZIONE:
         *
         * Anche se nel frattempo fosse già arrivata
         * l'approvazione, qui possiamo gestirlo dopo.
         *
         * Nel test iniziale ci aspettiamo IN_ATTESA.
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

        Long idTicket =
                ticketDao.insertPendingTicket(
                        richiesta.getIdCondominio(),
                        richiesta.getId(),
                        categoria,
                        priorita,
                        "TELEFONO",
                        area,
                        request.getDescrizione()
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
         * Associo il ticket alla telefonata inbound.
         *
         * In questo modo il post-call continuerà a salvare
         * trascrizione e audio sulla telefonata e la Dashboard
         * potrà recuperarli tramite id_ticket.
         */
        telefonataDao.updateIdTicket(
                request.getIdTelefonata(),
                idTicket
        );

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