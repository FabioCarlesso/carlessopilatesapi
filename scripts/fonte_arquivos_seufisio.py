"""Leitura das exportações do seufisio feitas pela interface (sem a API deles).

Alternativa a `GET api.seufisio.com.br` para quando a API não está acessível:

- **Clientes**: Relatórios → Clientes → "Exportar XLSX", com Situação
  "Ativos/Inativos" e as colunas CPF, Data Nascimento, E-mail, Endereço e
  Situação marcadas. Lido só com a biblioteca padrão (o XLSX é um zip de XML).
- **Prontuários**: Relatórios → Prontuários por profissional → "Gerar PDF",
  um arquivo por profissional e período. O texto é extraído com o `pdftotext`
  (pacote `poppler-utils`), o único requisito fora da biblioteca padrão.

As duas funções públicas devolvem dicionários no mesmo formato que a API do
seufisio devolvia, para que o mapeamento e a idempotência dos scripts sigam
idênticos nos dois caminhos.
"""

import re
import shutil
import subprocess
import unicodedata
import xml.etree.ElementTree as ET
import zipfile
from collections import OrderedDict

SITUACAO_ATIVO = 2
SITUACAO_INATIVO = 0
_NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

# Cabeçalho do XLSX → chave no formato da API do seufisio.
_COLUNAS_XLSX = {
    "matricula": "id",
    "cliente": "nome",
    "situacao": "situacao",
    "cpf": "cpf",
    "telefone": "telefone",
    "data nascimento": "data_nascimento",
    "e-mail": "email",
    "endereco": "endereco_completo",
}

_RE_ATENDIMENTO = re.compile(r"^Atendimento: (\d{2})/(\d{2})/(\d{4}) - (\d{2}:\d{2}) até")
_RE_PREENCHIDO_EM = re.compile(r"^(\d{2})/(\d{2})/(\d{4}) (\d{2}:\d{2})$")
_RODAPES_PDF = frozenset((
    "Sua vida com gestão mais fácil e dinâmica.",
    "seufisio | seufisio.com.br",
))
# O PDF quebra o parágrafo na largura da página (~120 caracteres). Linha com
# pelo menos este tamanho foi cortada pelo layout e continua na seguinte.
_LARGURA_QUEBRA_PDF = 100
# "Rua X Nº 123 - Bairro, Cidade - PR," (às vezes + ", 85800-000") — vazio sai "Nº - , - ,".
_RE_ENDERECO = re.compile(r"^(?P<log>.*?)\s*n[º°]\s*(?P<num>.*?)\s+-\s*(?P<bairro>.*?),\s*"
                          r"(?P<cidade>.*?)\s+-\s*(?P<uf>[^,]*)(?:,\s*(?P<cep>\d{5}-?\d{3}))?,?\s*$",
                          re.IGNORECASE)


def _sem_acento(txt):
    txt = unicodedata.normalize("NFKD", txt)
    return "".join(c for c in txt if not unicodedata.combining(c)).strip().lower()


def _coluna(ref):
    return re.match(r"[A-Z]+", ref).group()


def _ler_planilha(caminho):
    """Linhas da primeira aba como listas de dicionários {cabeçalho: valor}."""
    with zipfile.ZipFile(caminho) as z:
        nomes = z.namelist()
        compartilhadas = []
        if "xl/sharedStrings.xml" in nomes:
            raiz = ET.fromstring(z.read("xl/sharedStrings.xml"))
            compartilhadas = ["".join(t.text or "" for t in si.iter(f"{_NS}t"))
                              for si in raiz.findall(f"{_NS}si")]
        folha = ET.fromstring(z.read("xl/worksheets/sheet1.xml"))

    linhas = []
    for row in folha.iter(f"{_NS}row"):
        celulas = {}
        for c in row.findall(f"{_NS}c"):
            tipo = c.get("t")
            v = c.find(f"{_NS}v")
            if tipo == "inlineStr":
                valor = "".join(t.text or "" for t in c.iter(f"{_NS}t"))
            elif v is None or v.text is None:
                valor = ""
            elif tipo == "s":
                valor = compartilhadas[int(v.text)]
            else:
                valor = v.text
            celulas[_coluna(c.get("r"))] = valor.strip()
        linhas.append(celulas)
    if not linhas:
        return []
    cabecalho = linhas[0]
    return [{cabecalho[col]: valor for col, valor in linha.items() if col in cabecalho}
            for linha in linhas[1:]]


def separar_endereco(texto):
    """Campos de endereço da coluna única do XLSX; tudo None quando vazia."""
    campos = {"endereco": None, "endereco_numero": None, "bairro": None,
              "cidade": None, "uf": None, "cep": None}
    m = _RE_ENDERECO.match(texto or "")
    if not m:
        campos["endereco"] = (texto or "").strip(" ,-") or None
        return campos
    campos.update({
        "endereco": m["log"].strip() or None,
        "endereco_numero": m["num"].strip() or None,
        "bairro": m["bairro"].strip() or None,
        "cidade": m["cidade"].strip() or None,
        "uf": m["uf"].strip() or None,
        "cep": m["cep"],
    })
    return campos


def clientes_do_xlsx(caminho):
    """Clientes do relatório XLSX no formato de `GET /api/cliente/{id}`.

    Linhas sem situação (o total no rodapé da planilha) são descartadas.
    """
    clientes = []
    for linha in _ler_planilha(caminho):
        bruto = {}
        for cabecalho, valor in linha.items():
            chave = _COLUNAS_XLSX.get(_sem_acento(cabecalho))
            if chave:
                bruto[chave] = valor
        situacao = _sem_acento(bruto.get("situacao", ""))
        if situacao not in ("ativo", "inativo") or not bruto.get("nome"):
            continue
        cliente = {
            "id": int(bruto["id"]) if (bruto.get("id") or "").isdigit() else None,
            "nome": bruto["nome"],
            "situacao": SITUACAO_ATIVO if situacao == "ativo" else SITUACAO_INATIVO,
            "cpf": bruto.get("cpf") or None,
            "telefone": bruto.get("telefone") or None,
            "data_nascimento": bruto.get("data_nascimento") or None,
            "email": bruto.get("email") or None,
        }
        cliente.update(separar_endereco(bruto.get("endereco_completo")))
        clientes.append(cliente)
    return clientes


def texto_do_pdf(caminho):
    """Texto cru do PDF via `pdftotext` (sem `-layout`, que alinha colunas)."""
    if not shutil.which("pdftotext"):
        raise SystemExit("pdftotext não encontrado — instale o pacote poppler-utils")
    saida = subprocess.run(
        ["pdftotext", "-enc", "UTF-8", str(caminho), "-"],
        check=True, capture_output=True,
    )
    return saida.stdout.decode("utf-8")


def _juntar_quebras(linhas):
    """Refaz os parágrafos que o PDF cortou na largura da página."""
    paragrafos = []
    anterior = ""  # última linha *do PDF*, não o parágrafo já reconstruído
    for linha in linhas:
        linha = linha.strip()
        if not linha:
            continue
        if paragrafos and len(anterior) >= _LARGURA_QUEBRA_PDF and not anterior.endswith(":"):
            paragrafos[-1] = f"{paragrafos[-1]} {linha}"
        else:
            paragrafos.append(linha)
        anterior = linha
    return "\n".join(paragrafos) or None


def prontuarios_do_texto(texto):
    """Prontuários agrupados por cliente, a partir do texto do PDF.

    Devolve OrderedDict {nome do cliente: [linha, ...]} com cada linha no
    formato de `GET /api/cliente/{id}/prontuarios`, mais `prontuario_texto`
    (o texto já limpo, que dispensa a conversão de HTML).

    Estrutura de cada registro no PDF:
        Atendimento: DD/MM/AAAA - HH:MM até HH:MM
        Cliente: <nome>
        DD/MM/AAAA HH:MM          <- quando o prontuário foi preenchido
        <texto>
        Preenchido por: <profissional> - Registro: <conselho>

    Quando a página quebra logo depois de "Atendimento:", o PDF repete o
    cabeçalho na página seguinte; o que fica sem "Cliente:" é descartado.
    """
    por_cliente = OrderedDict()
    atendimento = None
    atual = None
    for bruta in texto.replace("\f", "\n").split("\n"):
        linha = bruta.strip()
        if linha in _RODAPES_PDF:
            continue
        m = _RE_ATENDIMENTO.match(linha)
        if m:
            dia, mes, ano, hora = m.groups()
            atendimento = (f"{ano}-{mes}-{dia}", f"{hora}:00")
            atual = None
            continue
        if linha.startswith("Cliente: ") and atendimento:
            atual = {"nome": linha[len("Cliente: "):].strip(), "atendimento": atendimento,
                     "preenchido_em": None, "corpo": []}
            atendimento = None
            continue
        if atual is None:
            continue
        if linha.startswith("Preenchido por:"):
            data, hora = atual["atendimento"]
            por_cliente.setdefault(atual["nome"], []).append({
                "data_atendimento": data,
                "hora_atendimento": hora,
                "prontuario_preenchido_em": atual["preenchido_em"],
                "prontuario_texto": _juntar_quebras(atual["corpo"]),
            })
            atual = None
            continue
        p = _RE_PREENCHIDO_EM.match(linha)
        if p and atual["preenchido_em"] is None and not atual["corpo"]:
            dia, mes, ano, hora = p.groups()
            atual["preenchido_em"] = f"{ano}-{mes}-{dia} {hora}:00"
            continue
        atual["corpo"].append(linha)
    return por_cliente


def prontuarios_dos_pdfs(caminhos):
    """Junta os prontuários de vários PDFs (um por profissional)."""
    por_cliente = OrderedDict()
    for caminho in caminhos:
        for nome, linhas in prontuarios_do_texto(texto_do_pdf(caminho)).items():
            por_cliente.setdefault(nome, []).extend(linhas)
    return por_cliente
