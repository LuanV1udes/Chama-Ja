package br.fatec.chamaja;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Escanear o QR em excesso (vários códigos por minuto, do mesmo IP) é bloqueado. */
@SpringBootTest(properties = {
        "chamaja.seed.enabled=false",
        "chamaja.rate-limit.por-minuto=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RateLimitTest {

    @Autowired
    MockMvc mvc;

    @Test
    void escanearOQrDemaisVezesNoMesmoMinutoEBloqueado() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/q/naoexiste").param("t", "x")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/q/naoexiste").param("t", "x")).andExpect(status().isTooManyRequests());
    }

    @Test
    void paginasSemEscanearNaoEntramNoLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/")).andExpect(status().isOk());
        }
    }
}
