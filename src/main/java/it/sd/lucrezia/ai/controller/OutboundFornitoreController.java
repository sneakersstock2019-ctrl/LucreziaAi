package it.sd.lucrezia.ai.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

	@Value("${lucrezia.api-public-secret}")
	private String apiSecret;
	
    private final OutboundFornitoreService outboundFornitoreService;

    @PostMapping
    public ResponseEntity<AvviaChiamataFornitoreResponse>
            avviaChiamata(
                    @RequestHeader(
                            value = "X-Lucrezia-Secret",
                            required = false
                    ) String receivedSecret,
                    @RequestBody
                    AvviaChiamataFornitoreRequest request) {

        validateSecret(receivedSecret);

        AvviaChiamataFornitoreResponse response =
                outboundFornitoreService.accodaChiamata(
                        request.getIdTicket(),
                        request.getIdFornitore()
                );

        return ResponseEntity
                .accepted()
                .body(response);
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
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            SecurityException e) {

        return ResponseEntity.status(401).body(
                Map.of(
                        "success", false,
                        "message", e.getMessage()
                )
        );
    }
    
    private void validateSecret(String receivedSecret) {

        if (receivedSecret == null
                || apiSecret == null
                || apiSecret.isBlank()) {

            throw new SecurityException(
                    "Chiamata non autorizzata"
            );
        }

        boolean valid = MessageDigest.isEqual(
                apiSecret.getBytes(StandardCharsets.UTF_8),
                receivedSecret.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            throw new SecurityException(
                    "Chiamata non autorizzata"
            );
        }
    }
}