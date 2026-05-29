package it.sd.demo.bot.condomini.controller;

import it.sd.demo.bot.condomini.bean.TicketView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        model.addAttribute("ruolo", "Amministratore");
        model.addAttribute("adminName", "Renato Zaino");
        model.addAttribute("pendingCount", 3);

        model.addAttribute("tickets", List.of(
                new TicketView("Salvatore D'Amato", "Condominio Viale Europa 175",
                        "Lampadina guasta scale condominiali 1° piano",
                        "elettricista", "P2", "In attesa", "29/05, 10:33"),
                new TicketView("Salvatore D'Amato", "Condominio Viale Europa 175",
                        "Lampadina bruciata scale condominiali 2° piano",
                        "elettricista", "P2", "In attesa", "29/05, 12:40"),
                new TicketView("Marta Raffone", "Condominio Via Puglia 16",
                        "Richiesta installazione pannelli fotovoltaici su tetto condominiale ad uso esclusivo",
                        "generale", "P1", "In attesa", "22/05, 11:15")
        ));

        return "dashboard";
    }
}