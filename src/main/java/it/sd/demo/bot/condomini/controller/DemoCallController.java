package it.sd.demo.bot.condomini.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import it.sd.demo.bot.condomini.service.TwilioCallService;

@RestController
@RequestMapping("/demo")
public class DemoCallController {

    @Autowired
    private TwilioCallService twilioCallService;

    @GetMapping("/call-ticket")
    public String callTicket(@RequestParam String telefono,
                             @RequestParam String nomeFornitore,
                             @RequestParam Long idTicket,
                             @RequestParam String condominio,
                             @RequestParam String categoria,
                             @RequestParam String priorita) {

        String numero = normalizeItalianPhone(telefono);

        twilioCallService.notifyTicketCreated(
                numero,
                nomeFornitore,
                idTicket,
                condominio,
                categoria,
                priorita
        );

        return "Chiamata inviata";
    }

    private String normalizeItalianPhone(String telefono) {

        if (telefono == null || telefono.isBlank()) {
            return null;
        }

        String numero = telefono.trim().replace(" ", "");

        if (numero.startsWith("+")) {
            return numero;
        }

        if (numero.startsWith("39")) {
            return "+" + numero;
        }

        return "+39" + numero;
    }
    
}