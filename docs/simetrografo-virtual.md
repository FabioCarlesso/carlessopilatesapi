# Simetrógrafo Virtual — Especificação Funcional

> **Projeto:** Carlesso Pilates (carlessopilatesapi + carlessopilatesfe)
> **Status:** Aguardando validação com fisioterapeuta
> **Data:** Julho/2026

## Conceito

O simetrógrafo virtual substitui o simetrógrafo físico (a armação quadriculada) por uma foto do paciente com grade e linha de prumo sobrepostas na tela. A fisioterapeuta marca os pontos anatômicos na foto e o sistema calcula automaticamente os desníveis e inclinações em graus, guardando tudo dentro da avaliação fisioterapêutica que já existe no sistema.

**Valor principal:** trocar o "olhômetro" por medidas objetivas registradas no prontuário, permitindo mostrar ao paciente, com números, a evolução do tratamento ("o desnível de ombro caiu de 4,1° para 1,4°").

---

## Tela 1 — Nova análise postural

**Contexto:** aba "Postural" dentro da avaliação fisioterapêutica do paciente (não é um cadastro separado).

### Funcionalidades

- [ ] Escolher a vista da foto: **frente, costas, lado direito ou lado esquerdo**
- [ ] Cada avaliação pode ter **uma análise por vista**
- [ ] Tirar foto na hora (celular/tablet) ou enviar da galeria
- [ ] Orientação na tela de como posicionar o paciente (em pé, fundo liso, aparelho na vertical e nivelado) — garante fotos comparáveis entre consultas

### Privacidade

- [ ] Consentimento do paciente para registro fotográfico, registrado na avaliação
- [ ] Fotos acessíveis apenas por profissionais autenticados no sistema

---

## Tela 2 — Editor de marcação

**Contexto:** foto aberta com grade e linha de prumo sobrepostas.

### Funcionalidades

- [ ] Grade quadriculada sobre a foto, com **espaçamento e visibilidade ajustáveis**
- [ ] **Linha de prumo** (vertical, vermelha) arrastável para alinhar com o ponto médio entre os pés
- [ ] **Marcação guiada ponto a ponto**: o sistema indica qual ponto marcar em seguida, sem precisar decorar a ordem
- [ ] Corrigir ponto **arrastando**; ação de **desfazer**
- [ ] **Zoom** para marcar com precisão regiões pequenas (olhos, tornozelos)
- [ ] Conjunto de pontos por vista:
  - Frente/Costas: pares esquerdo/direito — olhos, ombros (acrômios), quadril (EIAS), joelhos, tornozelos (maléolos)
  - Lateral: orelha (trago), ombro, quadril (trocânter), joelho, tornozelo
- [ ] **Salvar como rascunho** no meio; análise só é **concluída** com todos os pontos obrigatórios marcados

---

## Tela 3 — Resultados da análise

**Contexto:** exibida assim que os pontos são marcados.

### Funcionalidades

- [ ] Linhas de referência desenhadas sobre a foto (linha dos olhos, dos ombros, do quadril...)
- [ ] Medidas calculadas na hora:
  - Inclinação da cabeça (graus)
  - Desnível de ombros (graus)
  - Desnível de quadril (graus)
  - Desnível de joelhos (graus)
  - Desvio em relação à linha de prumo
- [ ] Valores fora da simetria esperada **destacados em vermelho**; demais em verde
- [ ] Medidas **sempre calculadas pelo sistema** (nunca digitadas); corrigir um ponto recalcula automaticamente
- [ ] Campo livre de **observações clínicas**
- [ ] Ao concluir, análise gravada no prontuário com foto, marcações e medidas

### Ponto a validar

Medidas em **centímetros** só aparecem quando há referência de tamanho conhecido na foto (ex.: régua na parede). Sem calibração, o sistema mostra apenas **ângulos**, que não dependem dela.

---

## Tela 4 — Evolução do paciente

**Contexto:** comparação entre duas análises do mesmo paciente e mesma vista.

### Funcionalidades

- [ ] Selecionar duas análises (ex.: primeira consulta × atual)
- [ ] Fotos anotadas **lado a lado**
- [ ] Tabela com valor de cada medida nas duas datas e a **diferença** (delta)
- [ ] Indicação de melhora/piora por medida
- [ ] Restrição: comparação apenas entre análises **concluídas** e da **mesma vista**

---

## Tela 5 — Relatório em PDF

### Funcionalidades

- [ ] Gerar PDF da análise individual: foto anotada + medidas + observações
- [ ] Gerar PDF da comparação de evolução: fotos lado a lado + tabela de diferenças
- [ ] Aviso no relatório: medida fotogramétrica de acompanhamento, **não substitui exame de imagem**
- [ ] Usos: arquivar no prontuário, entregar ao paciente, anexar a encaminhamento médico

---

## Regras gerais do prontuário

- Tudo fica guardado **dentro da avaliação fisioterapêutica existente**
- Análises antigas nunca são apagadas de verdade (**exclusão lógica**, histórico clínico preservado)
- Foto de análise concluída **não é substituída**; se ficou ruim, cancela-se a análise e cria-se outra

---

## Decisões técnicas já discutidas

| Tema | Decisão / Direção |
|---|---|
| Armazenamento de fotos | MVP: `bytea` no PostgreSQL em tabela própria (`avaliacoes_posturais_fotos`, V30) para o binário ficar fora das listagens, com compressão no frontend (~1080px). O acesso ao binário já passa pela interface `FotoStorage` (mesmo padrão do `EmailSender`); evolução: implementação Cloudflare R2 |
| Coordenadas dos landmarks | Normalizadas (0 a 1, relativas à imagem), persistidas como JSONB separado da foto |
| Cálculo das métricas | Trigonometria sobre os pontos (sem ML no MVP); métricas sempre derivadas, nunca editadas |
| Detecção automática | Fase futura: MediaPipe Pose / MoveNet via TensorFlow.js no browser |
| Entidade | `AvaliacaoPostural` (N:1 com `AvaliacaoFisioterapeutica`), campos: vista, foto, landmarks JSONB, métricas, status (`RASCUNHO`/`CONCLUIDA`), fio de prumo, calibração opcional |
| Duplicidade de vista | Máx. 1 análise por vista por avaliação — `409` na duplicata (a confirmar com fisioterapeuta) |
| PDF | Reaproveitar OpenPDF já usado nos relatórios de pagamento |
| LGPD | Foto de paciente = dado sensível de saúde: consentimento, acesso por role, URL assinada se usar object storage |

---

## Fases sugeridas de desenvolvimento

### Fase 1 — MVP (núcleo)
1. Entidade `AvaliacaoPostural` + migration + CRUD (API)
2. Upload/armazenamento de foto (bytea + compressão no frontend)
3. Componente Angular do editor: grade, prumo, marcação guiada, zoom, desfazer
4. Cálculo de métricas + tela de resultados
5. Status rascunho/concluída + observações

### Fase 2 — Evolução
6. Histórico de análises por paciente/vista
7. Comparação lado a lado com deltas

### Fase 3 — Documentação e refinamento
8. Export PDF (individual e comparação)
9. Migração de storage para R2 (se necessário)
10. Detecção automática de pontos (TensorFlow.js) — opcional

---

## Validação pendente (fisioterapeuta)

- [ ] Conjunto de pontos por vista está correto/completo?
- [ ] As medidas calculadas são as que ela usa na prática?
- [ ] Limiar do destaque vermelho (a partir de quantos graus é relevante?)
- [ ] Calibração em cm: vale o esforço ou ângulos bastam?
- [ ] Foto imutável após conclusão: engessa o dia a dia dela?
- [ ] Vista duplicada: bloquear (409) ou substituir?
- [ ] Funcionalidades faltantes ou desnecessárias (checklist do PDF)
