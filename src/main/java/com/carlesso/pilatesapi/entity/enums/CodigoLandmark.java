package com.carlesso.pilatesapi.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pontos anatômicos marcáveis no simetrógrafo virtual. O conjunto válido depende
 * da vista da análise: vistas frontais (frente/costas) usam pares
 * esquerdo/direito, enquanto as laterais usam pontos únicos.
 */
public enum CodigoLandmark {
    OLHO_ESQ,
    OLHO_DIR,
    OMBRO_ESQ,
    OMBRO_DIR,
    QUADRIL_ESQ,
    QUADRIL_DIR,
    JOELHO_ESQ,
    JOELHO_DIR,
    TORNOZELO_ESQ,
    TORNOZELO_DIR,
    ORELHA,
    OMBRO,
    QUADRIL,
    JOELHO,
    TORNOZELO;

    private static final Set<CodigoLandmark> FRONTAIS = EnumSet.of(
            OLHO_ESQ,
            OLHO_DIR,
            OMBRO_ESQ,
            OMBRO_DIR,
            QUADRIL_ESQ,
            QUADRIL_DIR,
            JOELHO_ESQ,
            JOELHO_DIR,
            TORNOZELO_ESQ,
            TORNOZELO_DIR);

    private static final Set<CodigoLandmark> LATERAIS = EnumSet.of(ORELHA, OMBRO, QUADRIL, JOELHO, TORNOZELO);

    /**
     * Pontos que pertencem à vista informada — todos obrigatórios para concluir a análise.
     */
    public static Set<CodigoLandmark> daVista(VistaPostural vista) {
        return vista.isLateral() ? LATERAIS : FRONTAIS;
    }
}
