package it.sd.lucrezia.ai.bean;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RetryOutboundResult {

    private String stato;
    private Integer tentativi;
    private Integer massimoTentativi;
    private LocalDateTime prossimoTentativo;
}