package br.fatec.chamaja;

import br.fatec.chamaja.web.ErrosDeEnvio;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite de tamanho estoura antes de o pedido chegar ao controller, então o tratador fica num
 * {@code @ControllerAdvice}. (O comportamento completo foi conferido com o Tomcat de verdade; o MockMvc
 * não aplica o limite de upload.)
 */
class ErrosDeEnvioTest {

    @Test
    void arquivoGrandeVoltaParaAListaComMensagemClara() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        FlashMap saida = new FlashMap();
        requisicao.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, saida);

        ModelAndView resposta = new ErrosDeEnvio().arquivoGrande(requisicao);

        assertThat(resposta.getViewName()).isEqualTo("redirect:/painel/promocoes");
        assertThat(saida.get("erro")).asString().contains("maior que 3 MB");
        // O tipo de erro que o Spring lança quando o arquivo passa do limite é o que o tratador declara.
        assertThat(MaxUploadSizeExceededException.class).isNotNull();
    }
}
