import json
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from import_anamneses_seufisio import (  # noqa: E402
    SEM_QUEIXA,
    carregar,
    data_do_titulo,
    map_to_anamnese,
)

LABELS = ["Objetivo", "Atividades", "Quais", "Queixa Principal", "Queixa Secundária",
          "Diagnóstico Médico", "História Pregressa de Doenças", "Avaliação Postural",
          "Força Muscular", "Amplitude do Movimento", "Perimetria/Exames", "Alergias/Cirurgias",
          "Medicamentos em Uso", "Histórico Familiar", "Restrições", "OBS Gerais"]


def _anamnese(data, **valores):
    return {"id": 1, "data": data, "campos": {label: valores.get(label, "") for label in LABELS}}


class DataDoTituloTest(unittest.TestCase):
    def test_extrai_a_data(self):
        self.assertEqual(data_do_titulo("Avaliação Anamnese seufisio - 14/04/2022"), "2022-04-14")
        self.assertIsNone(data_do_titulo(None))


class CarregarTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.dir)

    def _grava(self, nome, cliente, valores):
        with open(os.path.join(self.dir, nome), "w", encoding="utf-8") as f:
            json.dump({"cliente": cliente, "anamnese": 1, "titulo": "Avaliação X - 01/02/2023",
                       "campos": [[label, valores.get(label, "")] for label in LABELS]}, f)

    def test_agrupa_por_cliente_e_ignora_vazias(self):
        self._grava("a.json", 8, {"Objetivo": "Postura"})
        self._grava("b.json", 9, {})
        por_cliente, vazias = carregar(self.dir)
        self.assertEqual(list(por_cliente), [8])
        self.assertEqual(por_cliente[8][0]["data"], "2023-02-01")
        self.assertEqual(vazias, 1)


class MapToAnamneseTest(unittest.TestCase):
    def test_mapeia_campos_e_extras(self):
        p = map_to_anamnese(5, [_anamnese(
            "2022-04-14", **{"Objetivo": "Mobilidade", "Atividades": "Passado", "Quais": "corrida",
                             "Queixa Principal": "Lombar", "Queixa Secundária": "Joelho",
                             "Diagnóstico Médico": "Hérnia", "Alergias/Cirurgias": "Dipirona",
                             "Avaliação Postural": "Hipercifose", "Restrições": "Impacto"})])
        self.assertEqual(p["pacienteId"], 5)
        self.assertEqual(p["objetivos"], "Mobilidade")
        self.assertEqual(p["queixaPrincipal"], "Lombar\n\nQueixa secundária:\nJoelho")
        self.assertEqual(p["historicoDoencas"], "Diagnóstico médico:\nHérnia")
        self.assertEqual(p["alergias"], "Dipirona")
        self.assertEqual(p["restricoesMedicas"], "Impacto")
        self.assertEqual(p["nivelAtividadeFisica"], "Ativo no passado")
        self.assertIn("Atividades físicas:\ncorrida", p["observacoes"])
        self.assertIn("Avaliação postural:\nHipercifose", p["observacoes"])
        self.assertTrue(p["observacoes"].endswith("(anamnese de 14/04/2022)."))

    def test_obrigatorios_ganham_texto_padrao(self):
        p = map_to_anamnese(5, [_anamnese("2022-04-14", **{"Atividades": "Não"})])
        self.assertEqual(p["queixaPrincipal"], SEM_QUEIXA)
        self.assertTrue(p["objetivos"])
        self.assertEqual(p["nivelAtividadeFisica"], "Sedentário")

    def test_mais_recente_e_a_principal_e_a_anterior_vai_para_observacoes(self):
        p = map_to_anamnese(5, [
            _anamnese("2023-07-20", **{"Objetivo": "Antigo", "Queixa Principal": "Q1"}),
            _anamnese("2026-03-19", **{"Objetivo": "Novo", "Queixa Principal": "Q2"}),
        ])
        self.assertEqual(p["objetivos"], "Novo")
        self.assertIn("Anamnese anterior (20/07/2023):\nObjetivo: Antigo\nQueixa Principal: Q1",
                      p["observacoes"])


if __name__ == "__main__":
    unittest.main()
