# Calculadora de Calorias

Projeto em Clojure que implementa uma calculadora de calorias com menu de linha de comando e servidor local.

## Descrição

Este projeto armazena dados do usuário e transações de alimentos e exercícios em memória. O backend oferece endpoints HTTP e o frontend consome esses endpoints a partir de um menu CLI.

## Arquivos principais

- `src/calculadora/core.clj` - ponto de entrada do aplicativo.
- `src/calculadora/backend.clj` - servidor HTTP, persistência em memória e lógica de cálculos.
- `src/calculadora/frontend.clj` - interface de linha de comando e chamadas ao backend.

## Como executar

1. Instale o Clojure e o Leiningen.
2. Na raiz do projeto, execute:

```bash
lein run
```

3. Siga as opções do menu no terminal.

## Como usar

O fluxo do programa é:

1. Cadastrar os dados do usuário.
2. Registrar consumo de alimento.
3. Registrar atividade física.
4. Consultar extrato de transações.
5. Consultar saldo de calorias.
6. Sair.

### O que o menu faz

- **Cadastrar usuário**: grava altura, peso, idade e sexo.
- **Registrar consumo de alimento**: envia nome, data e quantidade ao backend. O backend usa uma API externa para buscar calorias e salva uma transação de ganho.
- **Registrar atividade física**: envia atividade, data e duração. O backend usa uma API externa para calcular calorias queimadas e salva uma transação de perda.
- **Consultar extrato**: mostra as transações em memória, com opção de filtrar por período.
- **Consultar saldo**: soma ganhos e perdas para exibir o saldo total de calorias.

## Dependências

As dependências estão definidas em `project.clj`:

- `org.clojure/clojure`
- `ring/ring-core`
- `ring/ring-jetty-adapter`
- `compojure`
- `ring/ring-json`
- `clj-http`

## Observações

- O projeto usa `atom` para manter o estado em memória.
- O frontend consome o backend local em `http://localhost:3002`.
- Para executar sem APIs externas em uma prova, você pode adaptar o backend para calcular calorias usando mapas locais de alimentos e atividades.

## Licença

Copyright © 2026

Este projeto usa a licença Eclipse Public License 2.0.
