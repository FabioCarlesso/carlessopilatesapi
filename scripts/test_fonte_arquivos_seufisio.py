import os
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fonte_arquivos_seufisio import (  # noqa: E402
    SITUACAO_ATIVO,
    SITUACAO_INATIVO,
    clientes_do_xlsx,
    prontuarios_do_texto,
    separar_endereco,
)
from import_evolucoes_seufisio import map_to_evolucao  # noqa: E402

_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"


def _xlsx(linhas):
    """XLSX mínimo, com strings compartilhadas, no formato da exportação do seufisio."""
    strings = []

    def idx(valor):
        if valor not in strings:
            strings.append(valor)
        return strings.index(valor)

    rows = []
    for r, linha in enumerate(linhas, start=1):
        cells = []
        for c, valor in enumerate(linha):
            ref = f"{chr(ord('A') + c)}{r}"
            if valor is None:
                continue
            cells.append(f'<c r="{ref}" t="s"><v>{idx(valor)}</v></c>')
        rows.append(f'<row r="{r}">{"".join(cells)}</row>')
    sheet = f'<worksheet xmlns="{_NS}"><sheetData>{"".join(rows)}</sheetData></worksheet>'
    sst = (f'<sst xmlns="{_NS}">'
           + "".join(f"<si><t>{s}</t></si>" for s in strings) + "</sst>")
    fd, caminho = tempfile.mkstemp(suffix=".xlsx")
    os.close(fd)
    with zipfile.ZipFile(caminho, "w") as z:
        z.writestr("xl/worksheets/sheet1.xml", sheet)
        z.writestr("xl/sharedStrings.xml", sst)
    return caminho


CABECALHO = ["Matrícula", "Cliente", "Situação", "Gênero", "Idade", "CPF", "Telefone",
             "Data Nascimento", "E-mail", "Endereço", "Data de Inativação", "Atendimentos",
             "Cadastro no seufisio", "Cliente desde"]


class ClientesDoXlsxTest(unittest.TestCase):
    def setUp(self):
        self.caminho = _xlsx([
            CABECALHO,
            ["7", "Maria Teste", "Ativo", "F", "40", "111.222.333-44", "(45) 99999-0000",
             "01/02/1985", "maria@exemplo.com", "Rua A Nº 10 - Centro, Cascavel - PR,",
             None, "12", "19/04/2022", "14/04/2022"],
            ["8", "João Teste", "Inativo", "M", "30", None, None, None, None,
             "Nº - , - ,", "03/04/2026", "3", "05/05/2022", None],
            [None, None, None, None, None, None, None, None, None, None, None, "15"],
        ])

    def tearDown(self):
        os.remove(self.caminho)

    def test_mapeia_colunas_no_formato_da_api(self):
        maria, joao = clientes_do_xlsx(self.caminho)
        self.assertEqual(maria["id"], 7)
        self.assertEqual(maria["nome"], "Maria Teste")
        self.assertEqual(maria["situacao"], SITUACAO_ATIVO)
        self.assertEqual(maria["cpf"], "111.222.333-44")
        self.assertEqual(maria["data_nascimento"], "01/02/1985")
        self.assertEqual(maria["email"], "maria@exemplo.com")
        self.assertEqual((maria["endereco"], maria["endereco_numero"], maria["bairro"],
                          maria["cidade"], maria["uf"]),
                         ("Rua A", "10", "Centro", "Cascavel", "PR"))
        self.assertEqual(joao["situacao"], SITUACAO_INATIVO)
        self.assertIsNone(joao["cpf"])
        self.assertIsNone(joao["endereco"])

    def test_datas_de_cadastro_e_inativacao(self):
        maria, joao = clientes_do_xlsx(self.caminho)
        self.assertEqual(maria["data_cadastro"], "14/04/2022")  # "Cliente desde" tem prioridade
        self.assertIsNone(maria["data_inativacao"])
        self.assertEqual(joao["data_cadastro"], "05/05/2022")  # sem "Cliente desde": cadastro no seufisio
        self.assertEqual(joao["data_inativacao"], "03/04/2026")

    def test_descarta_linha_de_totais(self):
        self.assertEqual(len(clientes_do_xlsx(self.caminho)), 2)


class SepararEnderecoTest(unittest.TestCase):
    def test_endereco_vazio(self):
        for vazio in ("Nº - , - ,", "nº - , - ,"):
            self.assertTrue(all(v is None for v in separar_endereco(vazio).values()))

    def test_cep_depois_da_uf(self):
        campos = separar_endereco("Rua B Nº 5 - Vila, Toledo - Paraná, 85900-000")
        self.assertEqual((campos["uf"], campos["cep"]), ("Paraná", "85900-000"))

    def test_formato_inesperado_vai_inteiro_no_logradouro(self):
        self.assertEqual(separar_endereco("Sítio sem número")["endereco"], "Sítio sem número")


TEXTO_PDF = """Prontuário de Evolução
Profissional: FULANA Período: 20/07/2026 até 24/11/2026

Atendimento: 20/07/2026 - 08:00 até 08:50
Cliente: Maria Teste
20/07/2026 08:00
QUEIXAS E RELATOS INICIAIS:
Chegou bem.
Preenchido por: FULANA - Registro: 1-F

Atendimento: 21/07/2026 - 16:00 até 16:50
\fSua vida com gestão mais fácil e dinâmica.

seufisio | seufisio.com.br

Atendimento: 21/07/2026 - 16:00 até 16:50
Cliente: João Teste
22/07/2026 09:15
{longa}
continuação do parágrafo.
Sua vida com gestão mais fácil e dinâmica.
seufisio | seufisio.com.br
Saiu bem.
Preenchido por: FULANA - Registro: 1-F
""".replace("{longa}", "x" * 110)


class ProntuariosDoTextoTest(unittest.TestCase):
    def setUp(self):
        self.por_cliente = prontuarios_do_texto(TEXTO_PDF)

    def test_agrupa_por_cliente_e_ignora_cabecalho_repetido_na_quebra_de_pagina(self):
        self.assertEqual(list(self.por_cliente), ["Maria Teste", "João Teste"])
        self.assertEqual(sum(len(v) for v in self.por_cliente.values()), 2)

    def test_campos_no_formato_da_api(self):
        linha = self.por_cliente["Maria Teste"][0]
        self.assertEqual(linha["data_atendimento"], "2026-07-20")
        self.assertEqual(linha["hora_atendimento"], "08:00:00")
        self.assertEqual(linha["prontuario_preenchido_em"], "2026-07-20 08:00:00")
        self.assertEqual(linha["prontuario_texto"], "QUEIXAS E RELATOS INICIAIS:\nChegou bem.")

    def test_remove_rodape_e_junta_linha_cortada_pelo_pdf(self):
        linha = self.por_cliente["João Teste"][0]
        self.assertEqual(linha["prontuario_preenchido_em"], "2026-07-22 09:15:00")
        self.assertEqual(linha["prontuario_texto"],
                         "x" * 110 + " continuação do parágrafo.\nSaiu bem.")

    def test_linha_do_pdf_passa_pelo_mapeamento_sem_conversao_de_html(self):
        linha = dict(self.por_cliente["Maria Teste"][0], prontuario_texto="a < b e c > d")
        evolucao, motivo = map_to_evolucao(linha)
        self.assertIsNone(motivo)
        self.assertEqual(evolucao["texto"], "a < b e c > d")
        self.assertEqual(evolucao["dataHoraRegistro"], "2026-07-20T08:00:00")
        self.assertEqual(evolucao["horario"], "08:00:00")


if __name__ == "__main__":
    unittest.main()
