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

        model.addAttribute("adminName", "Renato");
        model.addAttribute("pendingCount", 3);

        model.addAttribute("lavoriAutorizzati", List.of(
                new TicketView("Daniele Cecconato", "Condominio Via Roma 15",
                        "Lampadina guasta scale condominiali 1° piano",
                        "elettricista", "P2", "In attesa", "22/05, 22:33"),
                new TicketView("Daniele Cecconato", "Condominio Via Roma 15",
                        "Lampadina bruciata scale condominiali 2° piano",
                        "elettricista", "P2", "In attesa", "22/05, 22:40")
        ));

        model.addAttribute("daAssemblea", List.of(
                new TicketView("Daniele Cecconato", "Condominio Via Roma 15",
                        "Richiesta installazione pannelli fotovoltaici su tetto condominiale ad uso esclusivo",
                        "generale", "P1", "In attesa", "22/05, 14:42")
        ));

        return "dashboard";
    }
}