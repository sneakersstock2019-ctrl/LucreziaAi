package it.sd.lucrezia.ai.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.sd.lucrezia.ai.bean.ElevenLabsSipCallResult;
import it.sd.lucrezia.ai.bean.TelefonataOutbound;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TelefonataOutboundJob {

    private final TelefonataOutboundDao
            telefonataOutboundDao;

    private final ElevenLabsService elevenLabsService;

    @Scheduled(
        fixedDelayString = "${outbound.job.fixed-delay-ms}"
    )
    public void processaChiamateOutbound() {
    	System.out.println("Avvio Job Chiamata Fornitore");
        List<TelefonataOutbound> chiamate;

        try {
            chiamate =
                    telefonataOutboundDao
                            .claimChiamateDaAvviare(5);

        } catch (Exception e) {
            System.err.println(
                    "Errore claim chiamate outbound: "
                    + e.getMessage()
            );

            e.printStackTrace();
            return;
        }

        for (TelefonataOutbound chiamata : chiamate) {
            processaSingolaChiamata(chiamata);
        }
        System.out.println("Fine Job Chiamata Fornitore (" + chiamate.size() + " chiamate effettuate)");
    }

    private void processaSingolaChiamata(
            TelefonataOutbound chiamata) {

        try {
            System.out.println(
                    "Avvio chiamata outbound id="
                            + chiamata.getId()
                            + ", ticket="
                            + chiamata.getIdTicket()
                            + ", tentativo="
                            + chiamata.getTentativi()
            );

            ElevenLabsSipCallResult result =
                    elevenLabsService.avviaChiamata(
                            chiamata.getTelefonoDestinatario(),
                            "fornitore-"
                                    + chiamata.getIdFornitore(),
                            chiamata.getDynamicVariables()
                    );

            telefonataOutboundDao.updateAvviata(
                    chiamata.getId(),
                    result.getConversationId(),
                    result.getSipCallId()
            );

            System.out.println(
                    "Chiamata outbound avviata, id="
                            + chiamata.getId()
                            + ", conversationId="
                            + result.getConversationId()
            );

        } catch (Exception e) {

            System.err.println(
                    "Errore chiamata outbound id="
                            + chiamata.getId()
                            + ": "
                            + e.getMessage()
            );

            telefonataOutboundDao.programmaRetry(
                    chiamata.getId(),
                    e.getMessage(),
                    calcolaMinutiRetry(
                            chiamata.getTentativi()
                    )
            );
        }
    }

    private int calcolaMinutiRetry(int tentativo) {

        return switch (tentativo) {
            case 1 -> 1;
            case 2 -> 5;
            default -> 15;
        };
    }
}