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
