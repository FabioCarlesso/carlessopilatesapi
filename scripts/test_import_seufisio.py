#!/usr/bin/env python3
"""Testes unitários para scripts/import_seufisio.py — apenas stdlib (unittest)."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import import_seufisio as imp


class MapToPacienteTest(unittest.TestCase):
    def test_sem_nome_retorna_skip(self):
        payload, err = imp.map_to_paciente({"nome": "   "})
        self.assertIsNone(payload)
        self.assertEqual(err, "sem nome")

    def test_cpf_formatado_e_normalizado(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "cpf": "111.222.333-44"})
        self.assertIsNone(err)
        self.assertEqual(payload["cpf"], "11122233344")

    def test_cpf_em_branco_vira_none(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "cpf": "   "})
        self.assertIsNone(err)
        self.assertIsNone(payload["cpf"])

    def test_endereco_ausente_quando_todos_campos_vazios(self):
        payload, err = imp.map_to_paciente({"nome": "Ana"})
        self.assertIsNone(err)
        self.assertIsNone(payload["endereco"])

    def test_endereco_presente_quando_apenas_um_campo(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "cidade": "São Paulo"})
        self.assertIsNone(err)
        self.assertIsNotNone(payload["endereco"])
        self.assertEqual(payload["endereco"]["cidade"], "São Paulo")

    def test_cep_normalizado(self):
        payload, _ = imp.map_to_paciente({"nome": "Ana", "cep": "01001-000"})
        self.assertEqual(payload["endereco"]["cep"], "01001000")

    def test_email_em_branco_vira_none(self):
        payload, _ = imp.map_to_paciente({"nome": "Ana", "email": "  "})
        self.assertIsNone(payload["email"])

    def test_data_nascimento_iso(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "data_nascimento": "1985-03-12"})
        self.assertIsNone(err)
        self.assertEqual(payload["dataNascimento"], "1985-03-12")

    def test_data_nascimento_dd_mm_yyyy(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "data_nascimento": "12/03/1985"})
        self.assertIsNone(err)
        self.assertEqual(payload["dataNascimento"], "1985-03-12")

    def test_data_nascimento_invalida_retorna_skip(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "data_nascimento": "not-a-date"})
        self.assertIsNone(payload)
        self.assertIn("data_nascimento", err)


class MaskingTest(unittest.TestCase):
    def test_mask_cpf(self):
        self.assertEqual(imp.mask_cpf("11122233344"), "***.***.***-44")
        self.assertIsNone(imp.mask_cpf(None))
        self.assertEqual(imp.mask_cpf(""), None)
        self.assertEqual(imp.mask_cpf("X"), "***")

    def test_mask_email(self):
        self.assertEqual(imp.mask_email("ana.silva@email.com"), "a***@email.com")
        self.assertIsNone(imp.mask_email(None))
        self.assertEqual(imp.mask_email("naoehemail"), "***")

    def test_short_name(self):
        self.assertEqual(imp.short_name("Ana"), "A***")
        self.assertEqual(imp.short_name("Ana Silva"), "Ana S***")
        self.assertEqual(imp.short_name(""), "***")


class NormalizacaoTest(unittest.TestCase):
    def test_only_digits(self):
        self.assertEqual(imp.only_digits("111.222.333-44"), "11122233344")
        self.assertIsNone(imp.only_digits("---"))
        self.assertIsNone(imp.only_digits(None))

    def test_normalize_email(self):
        self.assertEqual(imp.normalize_email("  Ana@Email.COM "), "ana@email.com")
        self.assertIsNone(imp.normalize_email("   "))
        self.assertIsNone(imp.normalize_email(None))


class IndexPacientesTest(unittest.TestCase):
    def test_indexa_cpf_e_email_normalizados(self):
        cpfs, emails, fracas = imp.index_pacientes([
            {"cpf": "111.222.333-44", "email": "Ana@Email.com"},
            {"cpf": None, "email": None},
        ])
        self.assertEqual(cpfs, {"11122233344"})
        self.assertEqual(emails, {"ana@email.com"})

    def test_indexa_chave_fraca_de_quem_nao_tem_cpf_nem_email(self):
        _, _, fracas = imp.index_pacientes([
            {"cpf": None, "email": None, "nome": "Ana Silva", "dataNascimento": "1980-01-02"},
            {"cpf": "111", "email": None, "nome": "Com Cpf", "dataNascimento": "1980-01-02"},
        ])
        self.assertEqual(fracas, {("ana silva", "1980-01-02")})

    def test_chave_fraca_sem_data_de_nascimento(self):
        _, _, fracas = imp.index_pacientes([
            {"cpf": None, "email": None, "nome": "Só Nome", "dataNascimento": None},
        ])
        self.assertEqual(fracas, {("so nome", "")})


class MotivoDuplicadoTest(unittest.TestCase):
    def test_cpf_ja_cadastrado(self):
        payload = {"cpf": "11122233344", "email": "novo@email.com"}
        self.assertEqual(
            imp.motivo_duplicado(payload, {"11122233344"}, set()), "cpf_ja_cadastrado")

    def test_email_ja_cadastrado_ignora_caixa(self):
        payload = {"cpf": None, "email": "Ana@Email.com"}
        self.assertEqual(
            imp.motivo_duplicado(payload, set(), {"ana@email.com"}), "email_ja_cadastrado")

    def test_paciente_novo_nao_e_duplicado(self):
        payload = {"cpf": "99988877766", "email": "novo@email.com"}
        self.assertIsNone(imp.motivo_duplicado(payload, {"11122233344"}, {"ana@email.com"}))

    def test_sem_cpf_e_sem_email_usa_nome_e_nascimento(self):
        """Sem a chave fraca, esses pacientes eram recriados a cada execução."""
        payload = {"cpf": None, "email": None, "nome": "Ana Silva",
                   "dataNascimento": "1980-01-02"}
        self.assertEqual(
            imp.motivo_duplicado(payload, set(), set(), {("ana silva", "1980-01-02")}),
            "nome_data_nascimento_ja_cadastrado")

    def test_chave_fraca_ignora_acento_e_caixa(self):
        payload = {"cpf": None, "email": None, "nome": "  ANTÔNIO   josé ",
                   "dataNascimento": "1980-01-02"}
        self.assertEqual(
            imp.motivo_duplicado(payload, set(), set(), {("antonio jose", "1980-01-02")}),
            "nome_data_nascimento_ja_cadastrado")

    def test_mesma_pessoa_com_nascimento_diferente_nao_e_duplicado(self):
        payload = {"cpf": None, "email": None, "nome": "Ana Silva",
                   "dataNascimento": "1990-05-05"}
        self.assertIsNone(
            imp.motivo_duplicado(payload, set(), set(), {("ana silva", "1980-01-02")}))

    def test_chave_fraca_nao_se_aplica_a_quem_tem_cpf(self):
        payload = {"cpf": "99988877766", "email": None, "nome": "Ana Silva",
                   "dataNascimento": "1980-01-02"}
        self.assertIsNone(
            imp.motivo_duplicado(payload, set(), set(), {("ana silva", "1980-01-02")}))


class FetchPacientesLocaisTest(unittest.TestCase):
    """Regressão da issue #76: inativos precisam entrar no set de deduplicação."""

    def setUp(self):
        self.http_json_original = imp.http_json
        self.urls = []

    def tearDown(self):
        imp.http_json = self.http_json_original

    def _stub(self, paginas):
        def fake(method, url, headers=None, body=None):
            self.urls.append(url)
            return 200, paginas.get(url, {"content": [], "last": True})
        imp.http_json = fake

    def test_busca_ativos_e_inativos(self):
        base = "http://local"
        ativos = f"{base}/pacientes?ativo=true&page=0&size={imp.LOCAL_PAGE_SIZE}"
        inativos = f"{base}/pacientes?ativo=false&page=0&size={imp.LOCAL_PAGE_SIZE}"
        self._stub({
            ativos: {"content": [{"cpf": "111", "email": "a@x.com"}], "last": True},
            inativos: {"content": [{"cpf": "222", "email": "b@x.com"}], "last": True},
        })

        pacientes = imp.fetch_pacientes_locais(base, "tok")

        self.assertEqual(self.urls, [ativos, inativos])
        cpfs, emails, _ = imp.index_pacientes(pacientes)
        self.assertEqual(cpfs, {"111", "222"})
        self.assertEqual(emails, {"a@x.com", "b@x.com"})

    def test_pagina_ate_o_fim(self):
        base = "http://local"
        p0 = f"{base}/pacientes?ativo=true&page=0&size={imp.LOCAL_PAGE_SIZE}"
        p1 = f"{base}/pacientes?ativo=true&page=1&size={imp.LOCAL_PAGE_SIZE}"
        self._stub({
            p0: {"content": [{"cpf": "111"}], "last": False},
            p1: {"content": [{"cpf": "222"}], "last": True},
        })

        pacientes = imp.fetch_pacientes_locais(base, "tok")

        self.assertEqual(len(pacientes), 2)
        self.assertIn(p1, self.urls)

    def test_pagina_ate_o_fim_no_formato_do_spring_boot_34(self):
        """A API responde `{content, page}` sem `last` — conferido em runtime."""
        base = "http://local"
        p0 = f"{base}/pacientes?ativo=true&page=0&size={imp.LOCAL_PAGE_SIZE}"
        p1 = f"{base}/pacientes?ativo=true&page=1&size={imp.LOCAL_PAGE_SIZE}"
        self._stub({
            p0: {"content": [{"cpf": "111"}],
                 "page": {"size": 200, "number": 0, "totalElements": 2, "totalPages": 2}},
            p1: {"content": [{"cpf": "222"}],
                 "page": {"size": 200, "number": 1, "totalElements": 2, "totalPages": 2}},
        })

        pacientes = imp.fetch_pacientes_locais(base, "tok")

        self.assertEqual(len(pacientes), 2)
        self.assertIn(p1, self.urls)


class NormalizarUfTest(unittest.TestCase):
    """Regressão: `uf` por extenso estourava o VARCHAR(2) e virava 409."""

    def test_sigla_e_mantida_em_maiuscula(self):
        self.assertEqual(imp.normalizar_uf("pr"), "PR")
        self.assertEqual(imp.normalizar_uf(" SC "), "SC")

    def test_nome_por_extenso_vira_sigla(self):
        self.assertEqual(imp.normalizar_uf("Paraná"), "PR")
        self.assertEqual(imp.normalizar_uf("Parana"), "PR")
        self.assertEqual(imp.normalizar_uf("SÃO PAULO"), "SP")
        self.assertEqual(imp.normalizar_uf("rio grande do sul"), "RS")

    def test_vazio_vira_none(self):
        self.assertIsNone(imp.normalizar_uf(None))
        self.assertIsNone(imp.normalizar_uf("   "))

    def test_desconhecida_vira_none_em_vez_de_estourar_a_coluna(self):
        self.assertIsNone(imp.normalizar_uf("Californialândia"))

    def test_map_to_paciente_normaliza_a_uf(self):
        payload, err = imp.map_to_paciente({"nome": "Ana", "uf": "Paraná"})
        self.assertIsNone(err)
        self.assertEqual(payload["endereco"]["uf"], "PR")

    def test_map_to_paciente_nunca_manda_uf_maior_que_dois_chars(self):
        for valor in ("Paraná", "Parana", "PR", "Desconhecida", None):
            payload, _ = imp.map_to_paciente({"nome": "Ana", "cidade": "Curitiba", "uf": valor})
            uf = (payload.get("endereco") or {}).get("uf")
            self.assertTrue(uf is None or len(uf) == 2, f"uf inválida para {valor!r}: {uf!r}")


class ListSeufisioTest(unittest.TestCase):
    """Regressão: a API devolve 50 por página e ignora `per_page`."""

    def setUp(self):
        self.http_json_original = imp.http_json
        self.urls = []

    def tearDown(self):
        imp.http_json = self.http_json_original

    def _stub(self, paginas):
        def fake(method, url, headers=None, body=None):
            self.urls.append(url)
            return 200, paginas[url]
        imp.http_json = fake

    def test_percorre_todas_as_paginas(self):
        p1 = f"{imp.SEUFISIO_BASE}/cliente?page=1"
        p2 = f"{imp.SEUFISIO_BASE}/cliente?page=2"
        p3 = f"{imp.SEUFISIO_BASE}/cliente?page=3"
        self._stub({
            p1: {"data": [{"id": 1}], "last_page": 3, "total": 3},
            p2: {"data": [{"id": 2}], "last_page": 3, "total": 3},
            p3: {"data": [{"id": 3}], "last_page": 3, "total": 3},
        })

        clientes = imp.list_seufisio({})

        self.assertEqual([c["id"] for c in clientes], [1, 2, 3])
        self.assertEqual(self.urls, [p1, p2, p3])

    def test_pagina_unica(self):
        p1 = f"{imp.SEUFISIO_BASE}/cliente?page=1"
        self._stub({p1: {"data": [{"id": 1}], "last_page": 1, "total": 1}})

        self.assertEqual(len(imp.list_seufisio({})), 1)
        self.assertEqual(self.urls, [p1])

    def test_erro_encerra_o_script(self):
        def fake(method, url, headers=None, body=None):
            return 401, "não autorizado"
        imp.http_json = fake

        with self.assertRaises(SystemExit):
            imp.list_seufisio({})


class PaginaFinalTest(unittest.TestCase):
    def test_formato_antigo_usa_last(self):
        self.assertTrue(imp.pagina_final({"last": True}, [1]))
        self.assertFalse(imp.pagina_final({"last": False}, [1]))

    def test_formato_spring_boot_34_usa_page(self):
        meio = {"page": {"number": 0, "totalPages": 3}}
        fim = {"page": {"number": 2, "totalPages": 3}}
        self.assertFalse(imp.pagina_final(meio, [1]))
        self.assertTrue(imp.pagina_final(fim, [1]))

    def test_pagina_unica(self):
        self.assertTrue(imp.pagina_final({"page": {"number": 0, "totalPages": 1}}, [1]))

    def test_formato_desconhecido_segue_enquanto_a_pagina_vem_cheia(self):
        cheia = [None] * imp.LOCAL_PAGE_SIZE
        self.assertFalse(imp.pagina_final({}, cheia))
        self.assertTrue(imp.pagina_final({}, [None]))


class ParseDataTest(unittest.TestCase):
    def test_none(self):
        v, err = imp.parse_data_nascimento(None)
        self.assertIsNone(v)
        self.assertIsNone(err)

    def test_string_vazia(self):
        v, err = imp.parse_data_nascimento("   ")
        self.assertIsNone(v)
        self.assertIsNone(err)

    def test_tipo_invalido(self):
        v, err = imp.parse_data_nascimento(123)
        self.assertIsNone(v)
        self.assertIn("tipo inesperado", err)


if __name__ == "__main__":
    unittest.main()
