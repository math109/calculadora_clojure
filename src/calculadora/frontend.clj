(ns calculadora.frontend
  (:require [clj-http.client :as http]))

(defn obter-dados-usuario []
  (println "   CADASTRO DA CALCULADORA DE CALORIAS  ")
  (print "Digite sua altura em metros (ex: 1.75): ") (flush)
  (let [v-altura (Double/parseDouble (read-line))]
    (print "Digite seu peso em kg (ex: 70.5): ") (flush)
    (let [v-peso (Double/parseDouble (read-line))]
      (print "Digite sua idade: ") (flush)
      (let [v-idade (Integer/parseInt (read-line))]
        (print "Digite seu sexo (M/F): ") (flush)
        (let [v-sexo (clojure.string/upper-case (read-line))]
          {:altura v-altura :peso v-peso :idade v-idade :sexo v-sexo})))))

(defn obter-dados-alimento []
  (println "   REGISTRAR CONSUMO DE ALIMENTO       ")
  (print "Alimento (em ingles, ex: apple, rice, chicken): ") (flush)
  (let [nome (read-line)]
    (print "Data do consumo (ex: dia/mes/ano): ") (flush)
    (let [data (read-line)]
      (print "Quantidade consumida em gramas (ex: 150): ") (flush)
      (let [quantidade (read-line)]
        {:nome nome :data data :quantidade quantidade}))))

(defn obter-dados-exercicio []
  (println "   REGISTRAR ATIVIDADE FISICA    ")
  (print "Atividade (em ingles, ex: running, cycling, swimming): ") (flush)
  (let [atividade (read-line)]
    (print "Data (ex: dia/mes/ano): ") (flush)
    (let [data (read-line)]
      (print "Duracao em minutos (ex: 30): ") (flush)
      (let [duracao (read-line)]
        {:atividade atividade :data data :duracao duracao}))))

(defn imprimir-extrato-recursivo [transacoes]
  (when (seq transacoes)
    (let [t (first transacoes)]
      (println (str " > " (:data t) " | " (:tipo t) " | " (:descricao t) " | " (:calorias t) " kcal")))
    (recur (rest transacoes))))

(defn exibir-opcoes-menu [cadastrado?]
  (println "\n CALCULADORA DE CALORIAS ")
  (if cadastrado?
    (do
      (println "2. Registrar consumo de alimento")
      (println "3. Registrar atividade fisica")
      (println "4. Consultar extrato de transacoes")
      (println "5. Consultar saldo de calorias")
      (println "6. Sair"))
    (do
      (println "1. Cadastrar dados pessoais")
      (println "6. Sair")))
  (print "Escolha uma opcao: ")
  (flush))

(defn menu-principal [cadastrado?]
  (exibir-opcoes-menu cadastrado?)
  (let [opcao (read-line)]
    (cond
      (= opcao "1")
      (let [dados (obter-dados-usuario)
            sucesso? (try
                       (let [resposta (http/post "http://localhost:3002/api/usuario"
                                                 {:form-params dados
                                                  :content-type :json
                                                  :as :json})]
                         (println "\n[Front-end] " (get-in resposta [:body :mensagem]))
                         true)
                       (catch Exception e
                         (println "\n[Erro] Falha de conexao com o Back-end.")
                         false))]
        (if sucesso?
          (recur true)
          (recur cadastrado?)))

      (= opcao "6")
      (println "\nSaindo da aplicacao...")

      (and cadastrado? (= opcao "2"))
      (let [dados-alimento (obter-dados-alimento)]
        (try
          (let [resposta (http/post "http://localhost:3002/api/alimento"
                                    {:form-params dados-alimento
                                     :content-type :json
                                     :as :json})]
            (println "\n[Front-end] " (get-in resposta [:body :mensagem])))
          (catch Exception e
            (println "\n[Erro] Falha ao comunicar com o Back-end.")))
        (recur cadastrado?))

      (and cadastrado? (= opcao "3"))
      (let [dados-exercicio (obter-dados-exercicio)]
        (try
          (let [resposta (http/post "http://localhost:3002/api/exercicio"
                                    {:form-params dados-exercicio
                                     :content-type :json
                                     :as :json})]
            (println "\n[Front-end] " (get-in resposta [:body :mensagem])))
          (catch Exception e
            (println "\n[Erro] Falha ao comunicar com o Back-end.")))
        (recur cadastrado?))

      (and cadastrado? (= opcao "4"))
      (do 
        (println "\n CONSULTAR EXTRATO DE TRANSAÇÕES ")
        (println "1. Ver extrato completo")
        (println "2. Filtrar por periodo (datas)")
        (print "Escolha uma opcao: ") (flush)
        (let [sub-opcao (read-line)]
          (try
            (let [url "http://localhost:3002/api/extrato"
                  config-requisicao (if (= sub-opcao "2")
                                      (do
                                        (print "Digite a data de inicio (ex: dia/mes/ano): ") (flush)
                                        (let [ini (read-line)]
                                          (print "Digite a data de fim (ex: dia/mes/ano): ") (flush)
                                          (let [fim (read-line)]
                                            {:query-params {"inicio" ini "fim" fim} :as :json})))
                                      {:as :json})
                  resposta (http/get url config-requisicao)
                  transacoes (get-in resposta [:body :transacoes])]
              
              (println "\n EXTRATO DE TRANSACOES ")
              (if (empty? transacoes)
                (println "Nenhuma transacao encontrada.")
                (imprimir-extrato-recursivo transacoes)))
            (catch Exception e
              (println "\n[Erro] Falha ao consultar o extrato no Back-end."))))
        (recur cadastrado?))

      (and cadastrado? (= opcao "5"))
      (do 
        (println "\n CONSULTAR SALDO DE CALORIAS ")
        (println "1. Ver saldo total")
        (println "2. Filtrar por periodo (datas)")
        (print "Escolha uma opcao: ") (flush)
        (let [sub-opcao (read-line)]
          (try
            (let [url "http://localhost:3002/api/saldo"
                  config-requisicao (if (= sub-opcao "2")
                                      (do
                                        (print "Digite a data de inicio (ex: dia/mes/ano): ") (flush)
                                        (let [ini (read-line)]
                                          (print "Digite a data de fim (ex: dia/mes/ano): ") (flush)
                                          (let [fim (read-line)]
                                            {:query-params {"inicio" ini "fim" fim} :as :json})))
                                      {:as :json})
                  resposta (http/get url config-requisicao)
                  dados (:body resposta)]
              
              (println "\n RESULTADO DO SALDO DE CALORIAS ")
              (println "Total Consumido (Ganhos): " (:ganhos dados) " kcal")
              (println "Total Gasto (Perdas):     " (:perdas dados) " kcal")
              (println "SALDO FINAL:              " (:saldo dados) " kcal"))
            (catch Exception e
              (println "\n[Erro] Falha ao consultar o saldo no Back-end."))))
        (recur cadastrado?))

      :else
      (do
        (println "\nOpcao invalida! Tente novamente.")
        (recur cadastrado?)))))