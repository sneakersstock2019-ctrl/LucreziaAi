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
    public String callTicket() {

        twilioCallService.notifyTicketCreated("+393492123304");

        return "Chiamata inviata";
    }
}