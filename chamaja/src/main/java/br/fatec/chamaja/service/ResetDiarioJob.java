package br.fatec.chamaja.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RNF06: apaga os dados dos clientes ao fim do dia e reinicia a numeração das senhas. */
@Component
public class ResetDiarioJob {

    private static final Logger log = LoggerFactory.getLogger(ResetDiarioJob.class);

    private final FilaService fila;

    public ResetDiarioJob(FilaService fila) {
        this.fila = fila;
    }

    @Scheduled(cron = "${chamaja.reset-cron}", zone = "America/Sao_Paulo")
    public void zerarFilas() {
        fila.zerarTodas();
        log.info("Filas zeradas pela rotina diária.");
    }
}
