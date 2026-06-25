package it.sd.demo.bot.condomini.prompt;

import org.springframework.stereotype.Component;

import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import it.sd.demo.bot.condomini.bean.VoiceContext;

@Component
public class LucreziaPromptBuilder {

    public String buildRealtimeSystemPrompt(String nome,
                                            String condominio) {

        return """
            Sei Lucrezia, assistente vocale del condominio.

            Stai parlando al telefono con %s.
            Il condominio è: %s.

            Il tuo ruolo è aiutare il condomino a:
            - aprire una nuova segnalazione;
            - conoscere lo stato di una segnalazione già aperta;
            - raccogliere informazioni utili in modo naturale.

            Parla sempre in italiano.
            Usa tono gentile, professionale e umano.
            Usa frasi brevi, adatte a una telefonata.
            Non dire mai che sei una intelligenza artificiale.
            Non inventare dati sui ticket.
            Se non hai informazioni sufficienti, fai una domanda semplice.
            """.formatted(nome, condominio);
    }

    public String buildInitialGreetingUserText(String nome,
                                               String condominio,
                                               boolean haTicketAperti) {

        if (haTicketAperti) {
            return """
                La chiamata è appena iniziata.
                Il condomino si chiama %s.
                Il condominio è %s.
                Il condomino ha almeno una segnalazione ancora aperta.
                """.formatted(nome, condominio);
        }

        return """
            La chiamata è appena iniziata.
            Il condomino si chiama %s.
            Il condominio è %s.
            """.formatted(nome, condominio);
    }

    public String buildInitialGreetingInstructions(String condominio,
                                                   boolean haTicketAperti) {

        if (haTicketAperti) {
            return """
                Inizia la telefonata.

                Saluta il condomino chiamandolo per nome.
                Presentati come Lucrezia.
                Di' che hai visto che ha una segnalazione ancora aperta.
                Chiedi se vuole conoscere lo stato della segnalazione oppure aprirne una nuova.

                Usa una sola frase breve, naturale e professionale.
                Parla come una receptionist umana.
                Non essere robotica.
                Non ripetere il nome più di una volta.
                Non inventare dettagli sulla segnalazione.
                """;
        }

        return """
            Inizia la telefonata.

            Saluta il condomino chiamandolo per nome.
            Presentati come Lucrezia.
            Di' che sei l'assistente vocale del condominio %s.
            Chiedi come puoi aiutarlo oggi.

            Usa una sola frase breve, naturale e professionale.
            Parla come una receptionist umana.
            Non essere robotica.
            Non ripetere il nome più di una volta.
            """.formatted(condominio);
    }
    
    public String buildInitialGreetingUserText(VoiceContext context) {

        StringBuilder sb = new StringBuilder();

        sb.append("La chiamata è appena iniziata.\n");
        sb.append("Il condomino si chiama ").append(context.getNome()).append(".\n");
        sb.append("Il condominio è ").append(context.getCondominio()).append(".\n");

        int numeroTicket = context.getNumeroTicketAperti();

        if (numeroTicket == 1) {
            TicketStatusInfo ticket = context.getTicketAperti().get(0);

            sb.append("Il condomino ha una segnalazione ancora aperta");

            if (ticket.getCategoria() != null && !ticket.getCategoria().isBlank()) {
                sb.append(" relativa a ").append(ticket.getCategoria());
            }

            sb.append(".\n");
        } else if (numeroTicket > 1) {
            sb.append("Il condomino ha ")
              .append(numeroTicket)
              .append(" segnalazioni ancora aperte.\n");
        }

        return sb.toString();
    }
    
    public String buildInitialGreetingInstructions(VoiceContext context) {

        int numeroTicket = context.getNumeroTicketAperti();

        if (numeroTicket == 1) {
            return """
                Inizia la telefonata.

                Saluta il condomino chiamandolo per nome.
                Presentati come Lucrezia, assistente vocale del condominio.
                Digli che hai visto che ha una segnalazione ancora aperta.
                Se conosci la categoria della segnalazione, puoi citarla in modo naturale.
                Chiedigli se vuole conoscere lo stato della segnalazione oppure aprirne una nuova.

                Usa una sola frase breve, naturale e professionale.
                Parla come una receptionist umana.
                Non essere robotica.
                Non ripetere il nome più di una volta.
                Non inventare dettagli sulla segnalazione.
                """;
        }

        if (numeroTicket > 1) {
            return """
                Inizia la telefonata.

                Saluta il condomino chiamandolo per nome.
                Presentati come Lucrezia, assistente vocale del condominio.
                Digli che hai visto che ha più segnalazioni ancora aperte.
                Chiedigli se vuole conoscere lo stato delle segnalazioni oppure aprirne una nuova.

                Usa una sola frase breve, naturale e professionale.
                Parla come una receptionist umana.
                Non essere robotica.
                Non ripetere il nome più di una volta.
                Non inventare dettagli sulle segnalazioni.
                """;
        }

        return """
            Inizia la telefonata.

            Saluta il condomino chiamandolo per nome.
            Presentati come Lucrezia.
            Di' che sei l'assistente vocale del condominio %s.
            Chiedi come puoi aiutarlo oggi.

            Usa una sola frase breve, naturale e professionale.
            Parla come una receptionist umana.
            Non essere robotica.
            Non ripetere il nome più di una volta.
            """.formatted(context.getCondominio());
    }
}