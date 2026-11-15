package br.fatec.chamaja.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** RF15: gera o QR code que aponta para a página do estabelecimento (ZXing). */
@Service
public class QrCodeService {

    public byte[] png(String conteudo, int lado) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(
                    conteudo, BarcodeFormat.QR_CODE, lado, lado, Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", saida);
            return saida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Não foi possível gerar o QR code.", e);
        }
    }
}
