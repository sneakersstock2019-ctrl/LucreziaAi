package it.sd.demo.bot.condomini.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }
}