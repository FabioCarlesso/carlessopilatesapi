import os
import sys
import unittest
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from import_planos_seufisio import _mais_um_mes, _somar_meses, ciclos, horarios, plano_payload  # noqa: E402


def _pacote(inicio, fim, valor=580.0, texto="Terça às 09:00 com ANA na sala Sala 01, Quinta às 18:00 com BIA na sala Sala 01"):
    return {"plano": "PAC", "valor": valor, "inicio": inicio, "fim": fim,
            "horarios": horarios(texto), "periodicidade": "Mensal"}


def _fatura(venc, valor=580.0, paga=None):
    return {"numero": "1", "valor": valor, "vencimento": venc, "paga": paga}


class HorariosTest(unittest.TestCase):
    def test_extrai_dia_hora_e_profissional(self):
        self.assertEqual(horarios("Terça às 09:00 com CAMILA F. na sala Sala 01,\xa0Sábado às 08:00 com BIA na sala S2"),
                         [("TUESDAY", "09:00", "CAMILA F."), ("SATURDAY", "08:00", "BIA")])


class DatasTest(unittest.TestCase):
    def test_mesmo_calculo_da_api(self):
        self.assertEqual(_mais_um_mes(date(2026, 11, 18)), date(2026, 12, 17))
        self.assertEqual(_mais_um_mes(date(2026, 1, 31)), date(2026, 2, 27))
        self.assertEqual(_somar_meses(date(2026, 1, 31), 1), date(2026, 2, 28))
        self.assertEqual(_somar_meses(date(2026, 1, 5), -1), date(2025, 12, 5))


class PlanoPayloadTest(unittest.TestCase):
    def test_plano_mensal_com_menor_valor_e_horario_do_primeiro_dia(self):
        dados = {"pacotes": [_pacote(date(2026, 11, 3), date(2026, 12, 2))],
                 "faturas": [_fatura(date(2026, 10, 3), valor=544.96), _fatura(date(2026, 11, 3))]}
        p = plano_payload(7, dados)
        self.assertEqual((p["tipo"], p["frequenciaSemanal"], p["diasSemana"], p["horario"]),
                         ("MENSAL", "DUAS_VEZES", ["TUESDAY", "THURSDAY"], "09:00:00"))
        self.assertEqual(p["valor"], 544.96)
        self.assertEqual(p["dataInicio"], "2026-10-03")


class CiclosTest(unittest.TestCase):
    hoje = date(2026, 11, 24)

    def test_ancora_no_pacote_vigente_e_conta_meses_para_as_vencidas(self):
        dados = {"pacotes": [_pacote(date(2026, 11, 1), date(2026, 11, 30)),
                             _pacote(date(2026, 12, 1), date(2026, 12, 31))],
                 "faturas": [_fatura(date(2026, 10, 10)), _fatura(date(2026, 11, 10)), _fatura(date(2026, 12, 10))]}
        self.assertEqual([i for _, i in ciclos(dados, self.hoje)],
                         [date(2026, 10, 1), date(2026, 11, 1), date(2026, 12, 1)])

    def test_pacote_bimestral_usa_o_inicio_de_cada_pacote(self):
        dados = {"pacotes": [_pacote(date(2026, 10, 1), date(2026, 11, 30)),
                             _pacote(date(2026, 12, 1), date(2027, 1, 31))],
                 "faturas": [_fatura(date(2026, 10, 10)), _fatura(date(2026, 12, 10))]}
        self.assertEqual([i for _, i in ciclos(dados, self.hoje)], [date(2026, 10, 1), date(2026, 12, 1)])


if __name__ == "__main__":
    unittest.main()
