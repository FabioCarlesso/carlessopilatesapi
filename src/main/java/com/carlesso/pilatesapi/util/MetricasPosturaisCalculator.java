package com.carlesso.pilatesapi.util;

import com.carlesso.pilatesapi.dto.LandmarkDTO;
import com.carlesso.pilatesapi.dto.MetricasPosturaisDTO;
import com.carlesso.pilatesapi.entity.enums.CodigoLandmark;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculo trigonométrico das métricas do simetrógrafo virtual a partir dos
 * landmarks normalizados (0 a 1). Sem ML: cada desnível é o ângulo da reta que
 * liga os dois pontos correspondentes em relação à horizontal.
 *
 * <p>Como as coordenadas são normalizadas, os eixos só têm a mesma escala em
 * fotos quadradas. Quando a análise informa {@code proporcaoImagem}
 * (largura/altura), o deslocamento horizontal é reescalado por ela antes do
 * {@code atan2}, devolvendo o ângulo real da foto; sem proporção, o cálculo
 * assume imagem quadrada.
 *
 * <p>Convenção de sinal: o ângulo é medido do ponto mais à esquerda para o mais
 * à direita da imagem, no intervalo (-90, 90]. Positivo significa que o ponto da
 * direita está mais baixo na foto; zero significa pontos nivelados.
 */
public final class MetricasPosturaisCalculator {

    private static final int ESCALA_GRAUS = 2;
    private static final int ESCALA_NORMALIZADA = 4;
    private static final int ESCALA_CM = 2;

    private MetricasPosturaisCalculator() {}

    public static MetricasPosturaisDTO calcular(
            VistaPostural vista,
            List<LandmarkDTO> landmarks,
            BigDecimal linhaPrumoX,
            BigDecimal calibracaoCmPorUnidade,
            BigDecimal proporcaoImagem) {

        if (landmarks == null || landmarks.isEmpty()) {
            return MetricasPosturaisDTO.VAZIA;
        }

        Map<CodigoLandmark, LandmarkDTO> porCodigo = new EnumMap<>(CodigoLandmark.class);
        landmarks.forEach(l -> porCodigo.put(l.codigo(), l));

        double proporcao = proporcaoImagem != null ? proporcaoImagem.doubleValue() : 1.0;

        // Vistas laterais têm um ponto por região: não há par para medir desnível.
        BigDecimal inclinacaoCabeca = angulo(porCodigo, CodigoLandmark.OLHO_ESQ, CodigoLandmark.OLHO_DIR, proporcao);
        BigDecimal desnivelOmbros = angulo(porCodigo, CodigoLandmark.OMBRO_ESQ, CodigoLandmark.OMBRO_DIR, proporcao);
        BigDecimal desnivelQuadril =
                angulo(porCodigo, CodigoLandmark.QUADRIL_ESQ, CodigoLandmark.QUADRIL_DIR, proporcao);
        BigDecimal desnivelJoelhos = angulo(porCodigo, CodigoLandmark.JOELHO_ESQ, CodigoLandmark.JOELHO_DIR, proporcao);

        BigDecimal desvioPrumo = desvioPrumo(vista, porCodigo, linhaPrumoX);
        BigDecimal desvioPrumoCm = desvioPrumo != null && calibracaoCmPorUnidade != null
                ? desvioPrumo.multiply(calibracaoCmPorUnidade).setScale(ESCALA_CM, RoundingMode.HALF_UP)
                : null;

        return new MetricasPosturaisDTO(
                inclinacaoCabeca, desnivelOmbros, desnivelQuadril, desnivelJoelhos, desvioPrumo, desvioPrumoCm);
    }

    private static BigDecimal angulo(
            Map<CodigoLandmark, LandmarkDTO> porCodigo,
            CodigoLandmark codigoEsquerdo,
            CodigoLandmark codigoDireito,
            double proporcao) {
        LandmarkDTO a = porCodigo.get(codigoEsquerdo);
        LandmarkDTO b = porCodigo.get(codigoDireito);
        if (a == null || b == null) {
            return null;
        }

        double dx = (b.x().doubleValue() - a.x().doubleValue()) * proporcao;
        double dy = b.y().doubleValue() - a.y().doubleValue();
        if (dx == 0 && dy == 0) {
            return null;
        }

        double graus = Math.toDegrees(Math.atan2(dy, dx));
        // Normaliza para (-90, 90]: a reta é a mesma independentemente de qual
        // ponto foi marcado primeiro.
        if (graus > 90) {
            graus -= 180;
        } else if (graus <= -90) {
            graus += 180;
        }
        return BigDecimal.valueOf(graus).setScale(ESCALA_GRAUS, RoundingMode.HALF_UP);
    }

    /**
     * Desvio horizontal do tronco em relação à linha de prumo, em unidades
     * normalizadas: nas vistas frontais usa o ponto médio entre os acrômios; nas
     * laterais, o próprio acrômio.
     */
    private static BigDecimal desvioPrumo(
            VistaPostural vista, Map<CodigoLandmark, LandmarkDTO> porCodigo, BigDecimal linhaPrumoX) {
        if (linhaPrumoX == null) {
            return null;
        }

        BigDecimal referenciaX;
        if (vista.isLateral()) {
            LandmarkDTO ombro = porCodigo.get(CodigoLandmark.OMBRO);
            if (ombro == null) {
                return null;
            }
            referenciaX = ombro.x();
        } else {
            LandmarkDTO esquerdo = porCodigo.get(CodigoLandmark.OMBRO_ESQ);
            LandmarkDTO direito = porCodigo.get(CodigoLandmark.OMBRO_DIR);
            if (esquerdo == null || direito == null) {
                return null;
            }
            referenciaX = esquerdo.x().add(direito.x()).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
        }

        return referenciaX.subtract(linhaPrumoX).abs().setScale(ESCALA_NORMALIZADA, RoundingMode.HALF_UP);
    }
}
