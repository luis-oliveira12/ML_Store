package br.ufpb.iago.mlStore.main;

import br.ufpb.iago.mlStore.armazenamento.*;
import br.ufpb.iago.mlStore.excepcions.*;
import br.ufpb.iago.mlStore.gerenciamento.*;
import br.ufpb.iago.mlStore.modelo.*;
import br.ufpb.iago.mlStore.repositorio.RepositorioDeTipos;
import br.ufpb.iago.mlStore.repositorio.RepositorioDeUsuario;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static RepositorioDeUsuario repositorioUsuario;
    private static RepositorioDeTipos repositorioTipos;
    private static GerenciadorDeProduto gerenciadorProduto;
    private static GerenciadorDePedido gerenciadorPedido;

    private static InterfaceUsuario ui = new InterfaceUsuario();

    public static void main(String[] args) {
        inicializarSistema();
        exibirMenuPrincipal();
    }

    private static void inicializarSistema() {
        try {
            repositorioTipos = new RepositorioDeTipos();
            repositorioUsuario = new RepositorioDeUsuario();

            // 1. Busca se já existe a categoria exata "Eletrônico" no arquivo
            TipoProduto tipoEletronico = null;
            for (TipoProduto t : repositorioTipos.getTiposDeProdutos()) {
                if (t.getNome().equalsIgnoreCase("Eletrônico")) {
                    tipoEletronico = t;
                    break;
                }
            }

            // Se não existir, cria o tipo Eletrônico
            if (tipoEletronico == null) {
                tipoEletronico = new TipoProduto("Eletrônico", 15.0); // 15% de imposto
                repositorioTipos.addTipo(tipoEletronico);
            }

            // Carrega os produtos
            gerenciadorProduto = new GerenciadorDeProduto(repositorioTipos.getTiposDeProdutos());

            // ==========================================
            // FAXINA AUTOMÁTICA DE PRODUTOS ANTIGOS
            // Pega uma cópia da lista e deleta tudo que não for Eletrônico do seu .txt
            List<Produto> todosProdutos = new ArrayList<>(gerenciadorProduto.listarProdutos());
            for (Produto p : todosProdutos) {
                if (!p.getTipo().getNome().equalsIgnoreCase("Eletrônico")) {
                    gerenciadorProduto.removerProduto(p.getId()); // Isso apaga do arquivo automaticamente!
                }
            }
            // ==========================================

            // Carrega os pedidos passando apenas os produtos que sobraram (os eletrônicos)
            gerenciadorPedido = new GerenciadorDePedido(repositorioUsuario.acharTodos(), gerenciadorProduto.listarProdutos());

        } catch (IOException e) {
            ui.mostrarErro("Erro Crítico", "Erro ao inicializar os arquivos do sistema: " + e.getMessage());
            System.exit(1);
        }
    }

    // ================== MENUS PRINCIPAIS ==================

    private static void exibirMenuPrincipal() {
        boolean rodando = true;
        while (rodando) {
            String[] opcoes = {"Entrar como Admin", "Entrar como Cliente", "Cadastrar Cliente", "Sair"};
            int escolha = ui.mostrarMenu("Menu Principal", "Bem-vindo à mlStore Eletrônicos!\nEscolha uma opção:", opcoes);

            switch (escolha) {
                case 0 -> loginAdmin();
                case 1 -> loginCliente();
                case 2 -> cadastrarClienteUI();
                case 3, -1 -> {
                    rodando = false;
                    ui.mostrarMensagem("Despedida", "Saindo do sistema... Até logo!");
                }
            }
        }
    }

    private static void menuAdmin() {
        boolean rodando = true;
        while (rodando) {
            String[] opcoes = {"Cadastrar Eletrônico", "Listar Estoque", "Cadastrar Admin", "Voltar"};
            int escolha = ui.mostrarMenu("Menu Admin", "Painel de Administração", opcoes);

            switch (escolha) {
                case 0 -> cadastrarProdutoUI();
                case 1 -> listarProdutosUI();
                case 2 -> cadastrarAdminUI();
                case 3, -1 -> rodando = false;
            }
        }
    }

    private static void menuCliente(Cliente cliente) {
        boolean rodando = true;
        while (rodando) {
            String[] opcoes = {"Ver Catálogo de Eletrônicos", "Novo Pedido", "Meus Pedidos", "Voltar"};
            int escolha = ui.mostrarMenu("Menu Cliente", "Área do Cliente: " + cliente.getNomeCompleto(), opcoes);

            switch (escolha) {
                case 0 -> listarProdutosUI();
                case 1 -> realizarPedidoUI(cliente);
                case 2 -> listarPedidosClienteUI(cliente);
                case 3, -1 -> rodando = false;
            }
        }
    }

    // ================== AÇÕES DO CLIENTE ==================

    private static void realizarPedidoUI(Cliente cliente) {
        try {
            gerenciadorPedido.criarPedido(cliente);
            List<Pedido> pedidosDoCliente = gerenciadorPedido.listarPedidosPorCliente(cliente.getCpf());
            Pedido pedidoAtual = pedidosDoCliente.get(pedidosDoCliente.size() - 1);

            boolean adicionando = true;
            while (adicionando) {
                try {
                    int idBusca = ui.pedirInteiro("Digite o ID do eletrônico para adicionar ao carrinho\n(Ou digite 0 para finalizar o carrinho):");

                    if (idBusca == 0) {
                        adicionando = false;
                        break;
                    }

                    Produto produtoEncontrado = gerenciadorProduto.buscarProdutoPorId(idBusca);

                    // Validação de estoque
                    long qtdJaNoCarrinho = 0;
                    for (Produto p : pedidoAtual.getProdutos()) {
                        if (p.getId() == produtoEncontrado.getId()) qtdJaNoCarrinho++;
                    }

                    if (produtoEncontrado.getQuantidadeEstoque() > qtdJaNoCarrinho) {
                        gerenciadorPedido.adicionarProduto(pedidoAtual.getIdPedido(), produtoEncontrado);
                        ui.mostrarMensagem("Sucesso", produtoEncontrado.getNome() + " adicionado ao carrinho!");
                    } else {
                        ui.mostrarErro("Estoque Esgotado", "Sem estoque suficiente para " + produtoEncontrado.getNome() + ".\nLimite atingido!");
                    }

                } catch (ProdutoNaoEncontradoException e) {
                    ui.mostrarMensagem("Não Encontrado", "Eletrônico não encontrado no sistema.");
                }
            }

            try {
                gerenciadorPedido.finalizarPedido(pedidoAtual.getIdPedido());
                ui.mostrarMensagem("Sucesso", "Pedido " + pedidoAtual.getIdPedido() + " finalizado com sucesso!");

            } catch (PedidoVazioException e) {
                gerenciadorPedido.cancelarPedido(pedidoAtual.getIdPedido());
                ui.mostrarMensagem("Carrinho Vazio", "Pedido cancelado: " + e.getMessage());

            } catch (PedidoStatusInvalidoException | EstoqueInsuficienteException e) {
                ui.mostrarErro("Erro no Pedido", "Não foi possível finalizar a compra:\n" + e.getMessage());
            }

        } catch (OperacaoCanceladaException e) {
            // Silencioso
        } catch (Exception e) {
            ui.mostrarErro("Erro Interno", "Erro inesperado ao processar pedido: " + e.getMessage());
        }
    }

    private static void listarPedidosClienteUI(Cliente cliente) {
        List<Pedido> meusPedidos = gerenciadorPedido.listarPedidosPorCliente(cliente.getCpf());
        if (meusPedidos.isEmpty()) {
            ui.mostrarMensagem("Histórico", "Você ainda não possui pedidos.");
            return;
        }

        StringBuilder txtPedidos = new StringBuilder("Seu Histórico de Compras:\n\n");
        for (Pedido p : meusPedidos) {
            txtPedidos.append("ID: ").append(p.getIdPedido())
                    .append(" | Status: ").append(p.getStatus())
                    .append(" | Total: R$ ").append(String.format("%.2f", p.getValorTotal()))
                    .append("\n");
        }
        ui.mostrarMensagem("Meus Pedidos", txtPedidos.toString());
    }

    // ================== AÇÕES DO ADMIN ==================

    private static void cadastrarProdutoUI() {
        try {
            int id = 1;
            for (Produto p : gerenciadorProduto.listarProdutos()) {
                if (p.getId() >= id) {
                    id = p.getId() + 1;
                }
            }

            ui.mostrarMensagem("ID Automático", "O sistema gerou o ID " + id + " para este novo aparelho eletrônico.");
            String nome = ui.pedirTexto("Nome do Aparelho (Ex: Smartphone X):");
            double preco = ui.pedirDouble("Preço do Aparelho (Ex: 1500.50):");
            int estoque = ui.pedirInteiro("Quantidade em Estoque:");

            // Garante que o tipo do produto criado é sempre o "Eletrônico", ignorando outros (como roupas) que possam estar no txt
            TipoProduto tipoEletronico = null;
            for (TipoProduto t : repositorioTipos.getTiposDeProdutos()) {
                if (t.getNome().equalsIgnoreCase("Eletrônico")) {
                    tipoEletronico = t;
                    break;
                }
            }

            Produto novoProduto = new Produto(id, nome, preco, estoque, tipoEletronico);
            gerenciadorProduto.cadastrarProduto(novoProduto);

            ui.mostrarMensagem("Sucesso", "Eletrônico cadastrado com sucesso!");

        } catch (OperacaoCanceladaException e) {
            // Silencioso
        } catch (Exception e) {
            ui.mostrarErro("Erro", "Falha ao cadastrar: " + e.getMessage());
        }
    }

    private static void listarProdutosUI() {
        List<Produto> produtos = gerenciadorProduto.listarProdutos();
        if (produtos.isEmpty()) {
            ui.mostrarMensagem("Catálogo", "Nenhum eletrônico cadastrado no momento.");
            return;
        }

        StringBuilder txtProdutos = new StringBuilder("Catálogo de Eletrônicos:\n\n");
        for (Produto p : produtos) {
            txtProdutos.append("ID: ").append(p.getId()).append(" | ").append(p.toString()).append("\n");
        }
        ui.mostrarMensagem("Produtos", txtProdutos.toString());
    }

    // ================== LOGINS E CADASTROS DE USUÁRIOS ==================

    private static void loginAdmin() {
        try {
            String codigo = ui.pedirTexto("Digite o Código de Acesso do Admin:");
            String senha = ui.pedirTexto("Digite a Senha:");

            for (User u : repositorioUsuario.acharTodos()) {
                if (u instanceof Admin a && a.getCodigoDeAcesso().equals(codigo) && a.getPassword().equals(senha)) {
                    ui.mostrarMensagem("Login", "Bem-vindo, " + a.getNomeCompleto() + ".");
                    menuAdmin();
                    return;
                }
            }

            if (codigo.equals("admin") && senha.equals("admin")) {
                ui.mostrarMensagem("Login", "Bem-vindo, Administrador Padrão.");
                menuAdmin();
                return;
            }

            ui.mostrarErro("Erro", "Credenciais inválidas!");
        } catch (OperacaoCanceladaException e) { /* Silencioso */ }
    }

    private static void loginCliente() {
        try {
            String cpf = ui.pedirTexto("Digite seu CPF:");
            String senha = ui.pedirTexto("Digite sua Senha:");

            for (User u : repositorioUsuario.acharTodos()) {
                if (u instanceof Cliente c && c.getCpf().equals(cpf) && c.getPassword().equals(senha)) {
                    ui.mostrarMensagem("Login", "Bem-vindo, " + c.getNomeCompleto() + ".");
                    menuCliente(c);
                    return;
                }
            }
            ui.mostrarErro("Erro", "Credenciais inválidas!");
        } catch (OperacaoCanceladaException e) { /* Silencioso */ }
    }

    private static void cadastrarClienteUI() {
        try {
            String nomeCompleto = ui.pedirTexto("Nome Completo:");
            String email = ui.pedirTexto("Email:");
            String password = ui.pedirTexto("Senha:");

            String cpf = ui.pedirTexto("CPF (Apenas números, 11 dígitos):");
            if (!cpf.matches("\\d{11}")) {
                ui.mostrarErro("CPF Inválido", "O CPF é inválido! Deve conter exatamente 11 dígitos numéricos.");
                return;
            }

            String logradouro = ui.pedirTexto("Endereço - Logradouro (Rua/Av):");
            String numero = ui.pedirTexto("Endereço - Número:");
            String bairro = ui.pedirTexto("Endereço - Bairro:");
            String cidade = ui.pedirTexto("Endereço - Cidade:");
            String estado = ui.pedirTexto("Endereço - Estado (UF):");

            String complemento = ui.pedirTexto("Endereço - Complemento (Digite '-' se não houver):");

            Endereco endereco = new Endereco(logradouro, numero, bairro, cidade, estado, complemento);
            repositorioUsuario.cadastrarCliente(nomeCompleto, email, password, endereco, cpf);

            ui.mostrarMensagem("Sucesso", "Cliente registrado com sucesso! Já pode fazer o login.");
        } catch (OperacaoCanceladaException e) {
            // Silencioso
        } catch (Exception e) {
            ui.mostrarErro("Erro", "Falha ao registrar cliente: " + e.getMessage());
        }
    }

    private static void cadastrarAdminUI() {
        try {
            String nome = ui.pedirTexto("Nome do Admin:");
            String email = ui.pedirTexto("Email:");
            String senha = ui.pedirTexto("Senha:");

            repositorioUsuario.cadastrarAdmin(nome, email, senha, new Endereco());

            ui.mostrarMensagem("Sucesso", "Admin cadastrado com sucesso!");
        } catch (OperacaoCanceladaException e) { /* Silencioso */ }
        catch (Exception e) {
            ui.mostrarErro("Erro", "Falha ao registrar admin: " + e.getMessage());
        }
    }
}
