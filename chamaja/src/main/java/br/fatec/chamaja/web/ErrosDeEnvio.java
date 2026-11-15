package br.fatec.chamaja.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Erros que acontecem ANTES de o pedido chegar ao controller (por isso um tratador dentro do controller nunca
 * os veria): o arquivo enviado passou do limite do servidor. Em vez da página crua "HTTP 413", o operador volta
 * para a lista de promoções com uma mensagem clara.
 */
@ControllerAdvice
public class ErrosDeEnvio {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView arquivoGrande(HttpServletRequest requisicao) {
        RequestContextUtils.getOutputFlashMap(requisicao)
                .put("erro", "A imagem é maior que 3 MB. Reduza o arquivo e tente de novo.");
        return new ModelAndView("redirect:/painel/promocoes");
    }
}
