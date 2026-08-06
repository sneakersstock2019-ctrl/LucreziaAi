package it.sd.lucrezia.ai.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sd.lucrezia.ai.bean.AvviaChiamataFornitoreRequest;
import it.sd.lucrezia.ai.bean.AvviaChiamataFornitoreResponse;
import it.sd.lucrezia.ai.service.voice.OutboundFornitoreService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/outbound/fornitore")
public class OutboundFornitoreController {

    private final OutboundFornitoreService
            outboundFornitoreService;

    @PostMapping
    public ResponseEntity<AvviaChiamataFornitoreResponse>
            avviaChiamata(
                    @RequestBody
                    AvviaChiamataFornitoreRequest request) {

        AvviaChiamataFornitoreResponse response =
                outboundFornitoreService.avviaChiamata(
                        request.getIdTicket(),
                        request.getIdFornitore()
                );

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>>
            handleBadRequest(
                    IllegalArgumentException e) {

        return ResponseEntity.badRequest().body(
                Map.of(
                        "success", false,
                        "message", e.getMessage()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>>
            handleError(Exception e) {

        e.printStackTrace();

        return ResponseEntity.internalServerError().body(
                Map.of(
                        "success", false,
                        "message",
                        "Errore durante l'avvio della chiamata"
                )
        );
    }
}