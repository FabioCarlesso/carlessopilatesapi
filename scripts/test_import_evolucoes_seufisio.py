#!/usr/bin/env python3
"""Testes unitários para scripts/import_evolucoes_seufisio.py — apenas stdlib."""

import os
import sys
import unittest
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import import_evolucoes_seufisio as imp


class HtmlParaTextoTest(unittest.TestCase):
    def test_remove_tags_e_preserva_paragrafos(self):
        self.assertEqual(
            imp.html_para_texto("<p>Primeira linha</p><p>Segunda linha</p>"),
            "Primeira linha\nSegunda linha")

    def test_br_vira_quebra(self):
        self.assertEqual(imp.html_para_texto("linha um<br>linha dois"), "linha um\nlinha dois")

    def test_resolve_entidades(self):
        self.assertEqual(imp.html_para_texto("<p>flex&atilde;o &amp; extens&atilde;o</p>"),
                         "flexão & extensão")

    def test_nbsp_vira_espaco(self):
        self.assertEqual(imp.html_para_texto("<p>a&nbsp;b</p>"), "a b")

    def test_html_sem_conteudo_vira_none(self):
        self.assertIsNone(imp.html_para_texto("<p></p><div><br></div>"))

    def test_vazio_e_none(self):
        self.assertIsNone(imp.html_para_texto(""))
        self.assertIsNone(imp.html_para_texto(None))

    def test_colapsa_linhas_em_branco_excedentes(self):
        self.assertEqual(imp.html_para_texto("<p>a</p><p></p><p></p><p></p><p>b</p>"), "a\n\nb")


class NormalizarNomeTest(unittest.TestCase):
    def test_remove_acentos_e_caixa(self):
        self.assertEqual(imp.normalizar_nome("  Antônio   JOSÉ  "), "antonio jose")

    def test_vazio_vira_none(self):
        self.assertIsNone(imp.normalizar_nome("   "))
        self.assertIsNone(imp.normalizar_nome(None))


class ChaveSessaoTest(unittest.TestCase):
    def test_ignora_segundos(self):
        """A API omite os segundos quando são zero; o seufisio sempre os envia."""
        self.assertEqual(imp.chave_sessao("2026-07-21", "18:00:00"),
                         imp.chave_sessao("2026-07-21", "18:00"))

    def test_sem_horario_usa_meia_noite(self):
        self.assertEqual(imp.chave_sessao("2026-07-21", None), "2026-07-21T00:00")

    def test_sem_data_vira_none(self):
        self.assertIsNone(imp.chave_sessao(None, "18:00"))


class MapToEvolucaoTest(unittest.TestCase):
    def linha(self, **extra):
        base = {
            "atendimento_id": 99,
            "data_atendimento": "2026-07-21",
            "hora_atendimento": "18:00:00",
            "prontuario": "<p>Trabalho de mobilidade</p>",
            "prontuario_preenchido_em": None,
        }
        base.update(extra)
        return base

    def test_mapeia_campos(self):
        evolucao, motivo = imp.map_to_evolucao(self.linha())
        self.assertIsNone(motivo)
        self.assertEqual(evolucao["data"], "2026-07-21")
        self.assertEqual(evolucao["horario"], "18:00:00")
        self.assertEqual(evolucao["texto"], "Trabalho de mobilidade")

    def test_data_hora_registro_usa_preenchido_em(self):
        evolucao, _ = imp.map_to_evolucao(
            self.linha(prontuario_preenchido_em="2026-07-22 09:30:00"))
        self.assertEqual(evolucao["dataHoraRegistro"], "2026-07-22T09:30:00")

    def test_data_hora_registro_cai_para_o_atendimento(self):
        evolucao, _ = imp.map_to_evolucao(self.linha())
        self.assertEqual(evolucao["dataHoraRegistro"], "2026-07-21T18:00:00")

    def test_sem_horario_usa_meia_noite_no_registro(self):
        evolucao, _ = imp.map_to_evolucao(self.linha(hora_atendimento=""))
        self.assertIsNone(evolucao["horario"])
        self.assertEqual(evolucao["dataHoraRegistro"], "2026-07-21T00:00:00")

    def test_prontuario_vazio_e_pulado(self):
        _, motivo = imp.map_to_evolucao(self.linha(prontuario="<p></p>"))
        self.assertEqual(motivo, "sem_prontuario")

    def test_sem_data_e_pulado(self):
        _, motivo = imp.map_to_evolucao(self.linha(data_atendimento=""))
        self.assertEqual(motivo, "sem_data_atendimento")

    def test_filtro_desde(self):
        _, motivo = imp.map_to_evolucao(self.linha(data_atendimento="2025-01-01"),
                                        desde="2026-01-01")
        self.assertEqual(motivo, "anterior_ao_corte")
        evolucao, motivo = imp.map_to_evolucao(self.linha(), desde="2026-01-01")
        self.assertIsNone(motivo)
        self.assertIsNotNone(evolucao)


class ResolverPacienteTest(unittest.TestCase):
    def setUp(self):
        self.pacientes = [
            {"id": 1, "nome": "Antônio José", "cpf": "111.222.333-44", "ativo": True},
            {"id": 2, "nome": "Maria Silva", "cpf": None, "ativo": True},
            {"id": 3, "nome": "Maria Silva", "cpf": None, "ativo": False},
        ]
        self.por_cpf, self.por_nome = imp.indexar_para_match(self.pacientes)

    def test_casa_por_cpf(self):
        paciente, motivo = imp.resolver_paciente(
            {"nome": "Outro Nome", "cpf": "11122233344"}, self.por_cpf, self.por_nome)
        self.assertIsNone(motivo)
        self.assertEqual(paciente["id"], 1)

    def test_fallback_por_nome_normalizado(self):
        paciente, motivo = imp.resolver_paciente(
            {"nome": "ANTONIO JOSE", "cpf": None}, self.por_cpf, self.por_nome)
        self.assertIsNone(motivo)
        self.assertEqual(paciente["id"], 1)

    def test_nome_ambiguo_nao_chuta(self):
        paciente, motivo = imp.resolver_paciente(
            {"nome": "Maria Silva", "cpf": None}, self.por_cpf, self.por_nome)
        self.assertIsNone(paciente)
        self.assertEqual(motivo, "nome_ambiguo")

    def test_paciente_inexistente(self):
        paciente, motivo = imp.resolver_paciente(
            {"nome": "Fulano de Tal", "cpf": "99988877766"}, self.por_cpf, self.por_nome)
        self.assertIsNone(paciente)
        self.assertEqual(motivo, "paciente_nao_encontrado")


class HttpStubTest(unittest.TestCase):
    """Base para testes que trocam o http_json do módulo por um stub."""

    def setUp(self):
        self.original = imp.http_json
        self.chamadas = []

    def tearDown(self):
        imp.http_json = self.original

    def stub(self, respostas):
        """`respostas`: fila de (status, body) ou dict url -> (status, body)."""
        def fake(method, url, headers=None, body=None):
            self.chamadas.append((method, url, body))
            if isinstance(respostas, dict):
                return respostas[url]
            return respostas.pop(0)
        imp.http_json = fake


class FetchProntuariosTest(HttpStubTest):
    def test_pagina_ate_last_page(self):
        p1 = (f"{imp.SEUFISIO_BASE}/cliente/8/prontuarios"
              f"?rowsPerPage={imp.PRONTUARIOS_PAGE_SIZE}&page=1")
        p2 = (f"{imp.SEUFISIO_BASE}/cliente/8/prontuarios"
              f"?rowsPerPage={imp.PRONTUARIOS_PAGE_SIZE}&page=2")
        self.stub({
            p1: (200, {"data": [{"atendimento_id": 1}], "last_page": 2}),
            p2: (200, {"data": [{"atendimento_id": 2}], "last_page": 2}),
        })

        linhas, erro = imp.fetch_prontuarios({}, 8)

        self.assertIsNone(erro)
        self.assertEqual([l["atendimento_id"] for l in linhas], [1, 2])

    def test_erro_http_retorna_motivo(self):
        self.stub([(500, "boom")])
        linhas, erro = imp.fetch_prontuarios({}, 8)
        self.assertIsNone(linhas)
        self.assertIn("500", erro)


class SessoesExistentesTest(HttpStubTest):
    def test_normaliza_chaves(self):
        self.stub([(200, [{"id": 1, "status": "REALIZADA",
                           "data": "2026-07-21", "horario": "18:00"},
                          {"id": 2, "status": "REALIZADA",
                           "data": "2026-07-22", "horario": None}])])

        por_chave, erro = imp.sessoes_existentes("http://local", "tok", 1)

        self.assertIsNone(erro)
        self.assertEqual(set(por_chave), {"2026-07-21T18:00", "2026-07-22T00:00"})
        self.assertEqual([s["id"] for s in por_chave["2026-07-21T18:00"]], [1])

    def test_mesma_chave_acumula_as_sessoes(self):
        """Sem horário, os atendimentos do dia colapsam na mesma chave."""
        self.stub([(200, [{"id": 1, "status": "REALIZADA",
                           "data": "2026-07-21", "horario": None},
                          {"id": 2, "status": "REALIZADA",
                           "data": "2026-07-21", "horario": None}])])

        por_chave, erro = imp.sessoes_existentes("http://local", "tok", 1)

        self.assertIsNone(erro)
        self.assertEqual([s["id"] for s in por_chave["2026-07-21T00:00"]], [1, 2])

    def test_ignora_canceladas(self):
        self.stub([(200, [{"id": 1, "status": "CANCELADA",
                           "data": "2026-07-21", "horario": "18:00"}])])

        por_chave, erro = imp.sessoes_existentes("http://local", "tok", 1)

        self.assertIsNone(erro)
        self.assertEqual(por_chave, {})

    def test_404_retorna_erro(self):
        self.stub([(404, "não encontrado")])
        por_chave, erro = imp.sessoes_existentes("http://local", "tok", 1)
        self.assertIsNone(por_chave)
        self.assertIn("404", erro)


class EvolucaoExisteTest(HttpStubTest):
    def test_200_indica_que_ja_existe(self):
        self.stub([(200, {"id": 77})])
        existe, erro = imp.evolucao_existe("http://local", "tok", 55)
        self.assertTrue(existe)
        self.assertIsNone(erro)
        self.assertEqual(self.chamadas[0][1], "http://local/evolucoes-sessao/sessao/55")

    def test_404_indica_sessao_sem_evolucao(self):
        self.stub([(404, "não encontrada")])
        existe, erro = imp.evolucao_existe("http://local", "tok", 55)
        self.assertFalse(existe)
        self.assertIsNone(erro)

    def test_outro_status_e_erro(self):
        self.stub([(500, "boom")])
        existe, erro = imp.evolucao_existe("http://local", "tok", 55)
        self.assertIsNone(existe)
        self.assertIn("500", erro)


class CompletarEvolucaoTest(HttpStubTest):
    EVOLUCAO = {"data": "2026-07-21", "horario": "18:00:00",
                "dataHoraRegistro": "2026-07-21T18:00:00", "texto": "evolução"}

    def test_sessao_realizada_recebe_apenas_a_evolucao(self):
        self.stub([(201, {"id": 77})])
        fails = Counter()

        criou = imp.completar_evolucao("http://local", "tok",
                                       {"id": 55, "status": "REALIZADA"},
                                       self.EVOLUCAO, fails, "rotulo")

        self.assertTrue(criou)
        self.assertFalse(fails)
        self.assertEqual([(m, u) for m, u, _ in self.chamadas],
                         [("POST", "http://local/evolucoes-sessao")])

    def test_sessao_agendada_e_realizada_antes(self):
        """`PATCH realizar` só aceita AGENDADA — daí a checagem de status."""
        self.stub([(200, {"id": 55}), (201, {"id": 77})])
        fails = Counter()

        criou = imp.completar_evolucao("http://local", "tok",
                                       {"id": 55, "status": "AGENDADA"},
                                       self.EVOLUCAO, fails, "rotulo")

        self.assertTrue(criou)
        self.assertEqual([(m, u) for m, u, _ in self.chamadas], [
            ("PATCH", "http://local/sessoes/55/realizar"),
            ("POST", "http://local/evolucoes-sessao"),
        ])

    def test_falha_na_evolucao_e_contabilizada(self):
        self.stub([(422, "inválido")])
        fails = Counter()

        criou = imp.completar_evolucao("http://local", "tok",
                                       {"id": 55, "status": "REALIZADA"},
                                       self.EVOLUCAO, fails, "rotulo")

        self.assertFalse(criou)
        self.assertEqual(fails["post_evolucao_422"], 1)


class ImportarEvolucaoTest(HttpStubTest):
    EVOLUCAO = {"data": "2026-07-21", "horario": "18:00:00",
                "dataHoraRegistro": "2026-07-21T18:00:00", "texto": "evolução"}

    def test_cria_sessao_realiza_e_registra_evolucao(self):
        self.stub([(201, {"id": 55}), (200, {"id": 55}), (201, {"id": 77})])
        fails = Counter()

        criou = imp.importar_evolucao("http://local", "tok", 1, "PILATES",
                                      self.EVOLUCAO, fails, "rotulo")

        self.assertTrue(criou)
        self.assertFalse(fails)
        metodos_urls = [(m, u) for m, u, _ in self.chamadas]
        self.assertEqual(metodos_urls, [
            ("POST", "http://local/sessoes"),
            ("PATCH", "http://local/sessoes/55/realizar"),
            ("POST", "http://local/evolucoes-sessao"),
        ])
        corpo_sessao = self.chamadas[0][2]
        self.assertEqual(corpo_sessao["tipo"], "PILATES")
        self.assertEqual(corpo_sessao["data"], "2026-07-21")
        corpo_evolucao = self.chamadas[2][2]
        self.assertEqual(corpo_evolucao["sessaoId"], 55)
        self.assertEqual(corpo_evolucao["observacoesFisioterapeuta"], "evolução")

    def test_falha_ao_criar_sessao_nao_segue_adiante(self):
        self.stub([(422, "inválido")])
        fails = Counter()

        criou = imp.importar_evolucao("http://local", "tok", 1, "PILATES",
                                      self.EVOLUCAO, fails, "rotulo")

        self.assertFalse(criou)
        self.assertEqual(fails["post_sessao_422"], 1)
        self.assertEqual(len(self.chamadas), 1)

    def test_sessao_orfa_quando_evolucao_falha(self):
        self.stub([(201, {"id": 55}), (200, {"id": 55}), (409, "já existe")])
        fails = Counter()

        criou = imp.importar_evolucao("http://local", "tok", 1, "PILATES",
                                      self.EVOLUCAO, fails, "rotulo")

        self.assertFalse(criou)
        self.assertEqual(fails["post_evolucao_409"], 1)


class ProcessarClienteTest(HttpStubTest):
    def cfg(self, **extra):
        base = {"base_url": "http://local", "token": "tok", "tipo": "PILATES",
                "desde": None, "dry_run": False}
        base.update(extra)
        return base

    def linhas(self):
        return [
            {"atendimento_id": 1, "data_atendimento": "2026-07-20",
             "hora_atendimento": "18:00:00", "prontuario": "<p>ja importada</p>",
             "prontuario_preenchido_em": None},
            {"atendimento_id": 2, "data_atendimento": "2026-07-21",
             "hora_atendimento": "19:00:00", "prontuario": "<p>nova</p>",
             "prontuario_preenchido_em": None},
            {"atendimento_id": 3, "data_atendimento": "2026-07-22",
             "hora_atendimento": "20:00:00", "prontuario": "",
             "prontuario_preenchido_em": None},
        ]

    def sessao(self, sessao_id, data, horario, status="REALIZADA"):
        return {"id": sessao_id, "status": status, "data": data, "horario": horario}

    def linhas_sem_horario(self):
        """Dois atendimentos no mesmo dia, ambos sem `hora_atendimento`."""
        return [
            {"atendimento_id": 1, "data_atendimento": "2026-07-20",
             "hora_atendimento": "", "prontuario": "<p>manha</p>",
             "prontuario_preenchido_em": None},
            {"atendimento_id": 2, "data_atendimento": "2026-07-20",
             "hora_atendimento": "", "prontuario": "<p>tarde</p>",
             "prontuario_preenchido_em": None},
        ]

    def test_pula_sessao_existente_e_cria_apenas_a_nova(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00")]),  # sessões existentes
            (200, {"id": 70}),                                # sessão 50 já tem evolução
            (201, {"id": 55}), (200, {"id": 55}), (201, {"id": 77}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 1)
        self.assertEqual(skips["sessao_ja_existe"], 1)
        self.assertEqual(skips["sem_prontuario"], 1)
        self.assertFalse(fails)

    def test_reexecucao_nao_duplica(self):
        """Idempotência: com tudo já cadastrado, nada é criado."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00"),
                   self.sessao(51, "2026-07-21", "19:00")]),
            (200, {"id": 70}), (200, {"id": 71}),  # ambas já com evolução
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 0)
        self.assertEqual(skips["sessao_ja_existe"], 2)
        self.assertFalse(fails)

    def test_dois_atendimentos_sem_horario_no_mesmo_dia_sao_importados(self):
        """Sem horário a chave colapsa; o 2º atendimento não pode ser perdido."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, []),
            (201, {"id": 55}), (200, {"id": 55}), (201, {"id": 77}),
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente,
                                        self.linhas_sem_horario(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertFalse(skips)
        self.assertFalse(fails)

    def test_reexecucao_com_dois_atendimentos_sem_horario_nao_duplica(self):
        """As duas sessões do dia são consumidas uma a uma, não a mesma duas vezes."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", None),
                   self.sessao(51, "2026-07-20", None)]),
            (200, {"id": 70}), (200, {"id": 71}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente,
                                        self.linhas_sem_horario(), skips, fails)

        self.assertEqual(criadas, 0)
        self.assertEqual(skips["sessao_ja_existe"], 2)
        self.assertFalse(fails)

    def test_sessao_orfa_e_completada_na_reexecucao(self):
        """Execução interrompida deixou a sessão sem evolução: a próxima conserta."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00")]),
            (404, "sem evolução"),   # sessão 50 é órfã
            (201, {"id": 77}),       # evolução criada só para ela
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),  # 2026-07-21
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertEqual(skips["sessao_ja_existe"], 0)
        self.assertFalse(fails)
        # A sessão órfã recebeu a evolução sem uma nova sessão ser criada.
        self.assertEqual(self.chamadas[2][2]["sessaoId"], 50)

    def test_sessao_orfa_agendada_e_realizada_antes_da_evolucao(self):
        """`PATCH realizar` falhou antes: a sessão ficou AGENDADA e sem evolução."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00", status="AGENDADA")]),
            (404, "sem evolução"),
            (200, {"id": 50}),       # PATCH realizar
            (201, {"id": 77}),       # POST evolução
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertFalse(fails)
        self.assertEqual(self.chamadas[2][1], "http://local/sessoes/50/realizar")

    def test_sessao_cancelada_nao_bloqueia_a_importacao(self):
        """CANCELADA não recebe evolução; o atendimento vira uma sessão nova."""
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00", status="CANCELADA")]),
            (201, {"id": 55}), (200, {"id": 55}), (201, {"id": 77}),
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertEqual(skips["sessao_ja_existe"], 0)
        self.assertFalse(fails)

    def test_falha_ao_consultar_evolucao_nao_cria_sessao_duplicada(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00")]),
            (500, "boom"),
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 1)
        self.assertEqual(fails["evolucao_existe"], 1)

    def test_paciente_inativo_e_reativado_e_reinativado(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": False}
        self.stub([
            (204, None),                                          # ativar
            (200, []),                                            # sessões existentes
            (201, {"id": 55}), (200, {"id": 55}), (201, {"id": 77}),  # 2026-07-20
            (201, {"id": 56}), (200, {"id": 56}), (201, {"id": 78}),  # 2026-07-21
            (204, None),                                          # inativar
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertEqual(self.chamadas[0][1], "http://local/pacientes/1/ativar")
        self.assertEqual(self.chamadas[-1][1], "http://local/pacientes/1/inativar")

    def test_paciente_inativo_e_reinativado_mesmo_com_falha(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": False}
        self.stub([
            (204, None),        # ativar
            (500, "boom"),      # sessões existentes falha
            (204, None),        # inativar (finally)
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(), paciente, self.linhas(), skips, fails)

        self.assertEqual(criadas, 0)
        self.assertEqual(fails["sessoes_existentes"], 1)
        self.assertEqual(self.chamadas[-1][1], "http://local/pacientes/1/inativar")

    def test_dry_run_nao_grava(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([(200, [])])  # apenas a consulta de sessões existentes
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(dry_run=True), paciente,
                                        self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertEqual([m for m, _, _ in self.chamadas], ["GET"])

    def test_dry_run_com_sessao_orfa_apenas_consulta(self):
        paciente = {"id": 1, "nome": "Ana Silva", "ativo": True}
        self.stub([
            (200, [self.sessao(50, "2026-07-20", "18:00")]),
            (404, "sem evolução"),
        ])
        skips, fails = Counter(), Counter()

        criadas = imp.processar_cliente(self.cfg(dry_run=True), paciente,
                                        self.linhas(), skips, fails)

        self.assertEqual(criadas, 2)
        self.assertEqual([m for m, _, _ in self.chamadas], ["GET", "GET"])


class ValidarTipoSessaoTest(unittest.TestCase):
    def test_tipos_do_enum_sao_aceitos(self):
        self.assertIsNone(imp.validar_tipo_sessao("PILATES"))
        self.assertIsNone(imp.validar_tipo_sessao("FISIOTERAPIA"))

    def test_caixa_errada_e_rejeitada(self):
        """A API compara com o enum; 'pilates' quebraria todos os POST /sessoes."""
        erro = imp.validar_tipo_sessao("pilates")
        self.assertIn("SEUFISIO_TIPO_SESSAO", erro)
        self.assertIn("PILATES", erro)

    def test_valor_desconhecido_e_rejeitado(self):
        self.assertIsNotNone(imp.validar_tipo_sessao("MASSAGEM"))


class ParseArgsTest(unittest.TestCase):
    def test_desde_invalida_encerra(self):
        with self.assertRaises(SystemExit):
            imp.parse_args(["--desde", "21/07/2026"])

    def test_opcoes_basicas(self):
        args = imp.parse_args(["--dry-run", "--desde", "2026-01-01",
                               "--cliente-id", "8", "--cliente-id", "9"])
        self.assertTrue(args.dry_run)
        self.assertEqual(args.desde, "2026-01-01")
        self.assertEqual(args.cliente_ids, [8, 9])


if __name__ == "__main__":
    unittest.main()
