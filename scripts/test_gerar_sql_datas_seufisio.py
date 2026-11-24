import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fonte_arquivos_seufisio import SITUACAO_ATIVO, SITUACAO_INATIVO  # noqa: E402
from gerar_sql_datas_seufisio import data_iso, gerar_sql, montar_updates  # noqa: E402


def _cliente(nome, situacao, cadastro="10/03/2022", inativacao=None, email=None):
    return {"id": 1, "nome": nome, "email": email, "cpf": None, "situacao": situacao,
            "data_cadastro": cadastro, "data_inativacao": inativacao}


PACIENTES = [
    {"id": 10, "nome": "Maria Teste", "email": "maria@x.com", "ativo": True},
    {"id": 11, "nome": "João Teste", "email": None, "ativo": False},
    {"id": 12, "nome": "Ana Teste", "email": None, "ativo": True},
]


class DataIsoTest(unittest.TestCase):
    def test_converte_e_tolera_vazio(self):
        self.assertEqual(data_iso("05/07/2022"), "2022-07-05")
        self.assertIsNone(data_iso(""))
        self.assertIsNone(data_iso("31/02/2022"))


class MontarUpdatesTest(unittest.TestCase):
    def test_casa_por_email_e_por_nome(self):
        updates, skips = montar_updates([
            _cliente("Maria Outro Nome", SITUACAO_ATIVO, email="MARIA@x.com"),
            _cliente("joão  teste", SITUACAO_INATIVO, inativacao="01/02/2024"),
        ], PACIENTES)
        self.assertEqual([u["id"] for u in updates], [10, 11])
        self.assertEqual(updates[1]["data_inativacao"], "2024-02-01")
        self.assertFalse(skips)

    def test_ativo_ignora_data_de_inativacao(self):
        updates, _ = montar_updates(
            [_cliente("Maria Teste", SITUACAO_ATIVO, inativacao="01/02/2024")], PACIENTES)
        self.assertIsNone(updates[0]["data_inativacao"])

    def test_situacao_divergente_e_nao_encontrado_ficam_de_fora(self):
        updates, skips = montar_updates([
            _cliente("Ana Teste", SITUACAO_INATIVO, inativacao="01/02/2024"),
            _cliente("Fulano", SITUACAO_ATIVO),
        ], PACIENTES)
        self.assertEqual(updates, [])
        self.assertEqual(skips, {"situacao_divergente": 1, "paciente_nao_encontrado": 1})


class GerarSqlTest(unittest.TestCase):
    def test_sql_transacional_com_guarda_de_situacao(self):
        sql = gerar_sql([
            {"id": 10, "data_cadastro": "2022-03-10", "data_inativacao": None, "ativo": True},
            {"id": 11, "data_cadastro": None, "data_inativacao": "2024-02-01", "ativo": False},
        ], "o'admin@x.com")
        self.assertTrue(sql.splitlines()[2] == "BEGIN;" and sql.rstrip().endswith("COMMIT;"))
        self.assertIn("SET data_cadastro = DATE '2022-03-10', version = version + 1", sql)
        self.assertIn("WHERE id = 10 AND ativo;", sql)
        self.assertIn("SET data_inativacao = DATE '2024-02-01'", sql)
        self.assertIn("WHERE id = 11 AND NOT ativo;", sql)
        self.assertIn("alterado_por = 'o''admin@x.com'", sql)


if __name__ == "__main__":
    unittest.main()
