package it.sd.lucrezia.ai.service.voice;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.sd.lucrezia.ai.bean.AvviaChiamataFornitoreResponse;
import it.sd.lucrezia.ai.bean.TelefonataOutbound;
import it.sd.lucrezia.ai.bean.TicketFornitoreCallData;
import it.sd.lucrezia.ai.dao.OutboundTicketDao;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboundFornitoreService {

    private final OutboundTicketDao outboundTicketDao;
    private final TelefonataOutboundDao telefonataOutboundDao;
    private final ElevenLabsService elevenLabsService;

    @Value("${lucrezia.dashboard-public-base-url}")
    private String dashboardBaseUrl;

    @Transactional
    public AvviaChiamataFornitoreResponse accodaChiamata(
            Long idTicket,
            Long idFornitore) {

        validateRequest(idTicket, idFornitore);

        TicketFornitoreCallData dati =
                outboundTicketDao.loadOutboundData(
                        idTicket,
                        idFornitore
                );

        if (dati == null) {
            throw new IllegalArgumentException(
                    "Ticket o fornitore non trovato oppure "
                    + "ticket non assegnato al fornitore indicato"
            );
        }

        dati.setDashboardUrl(buildDashboardUrl(idTicket));

        Map<String, Object> dynamicVariables =
                buildDynamicVariables(dati);

        TelefonataOutbound telefonata = new TelefonataOutbound();

        telefonata.setTipoChiamata("ASSEGNAZIONE_FORNITORE");
        telefonata.setIdTicket(dati.getIdTicket());
        telefonata.setIdFornitore(dati.getIdFornitore());
        telefonata.setIdCondominio(dati.getIdCondominio());

        telefonata.setTelefonoDestinatario(
                dati.getTelefonoFornitore()
        );

        telefonata.setNominativoDestinatario(
                dati.getNomeFornitore()
        );

        telefonata.setAgentId(
                elevenLabsService.getFornitoreAgentId()
        );

        telefonata.setAgentPhoneNumberId(
                elevenLabsService.getAgentPhoneNumberId()
        );

        telefonata.setStato("DA_AVVIARE");
        telefonata.setTentativi(0);
        telefonata.setMassimoTentativi(3);
        telefonata.setDynamicVariables(dynamicVariables);

        Long idTelefonata =
                telefonataOutboundDao.insert(telefonata);

        dynamicVariables.put(
                "telefonata_outbound_id",
                idTelefonata
        );

        telefonataOutboundDao.updateDynamicVariables(
                idTelefonata,
                dynamicVariables
        );

        return new AvviaChiamataFornitoreResponse(
                true,
                idTelefonata,
                null,
                null,
                "Chiamata accodata correttamente"
        );
    }

    private Map<String, Object> buildDynamicVariables(
            TicketFornitoreCallData dati) {

        Map<String, Object> variables =
                new LinkedHashMap<>();

        variables.put(
                "tipo_chiamata",
                "ASSEGNAZIONE_FORNITORE"
        );

        variables.put("ticket_id", dati.getIdTicket());
        variables.put("fornitore_id", dati.getIdFornitore());

        variables.put(
                "nome_fornitore",
                safe(dati.getNomeFornitore())
        );

        variables.put(
                "telefono_fornitore",
                safe(dati.getTelefonoFornitore())
        );

        variables.put(
                "condominio",
                safe(dati.getCondominio())
        );

        variables.put(
                "indirizzo_condominio",
                safe(dati.getIndirizzoCondominio())
        );

        variables.put(
                "categoria",
                safe(dati.getCategoria())
        );

        variables.put(
                "priorita",
                safe(dati.getPriorita())
        );

        variables.put(
                "descrizione_ticket",
                safe(dati.getDescrizione())
        );

        variables.put(
                "nome_condomino",
                safe(dati.getNomeCondomino())
        );

        variables.put(
                "telefono_condomino",
                safe(dati.getTelefonoCondomino())
        );

        variables.put(
                "dashboard_url",
                safe(dati.getDashboardUrl())
        );

        return variables;
    }

    private String buildDashboardUrl(Long idTicket) {

        String base = dashboardBaseUrl;

        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base
                + "/fornitore/ticket/open/"
                + idTicket;
    }

    private void validateRequest(
            Long idTicket,
            Long idFornitore) {

        if (idTicket == null) {
            throw new IllegalArgumentException(
                    "ID ticket obbligatorio"
            );
        }

        if (idFornitore == null) {
            throw new IllegalArgumentException(
                    "ID fornitore obbligatorio"
            );
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value.trim();
    }
}