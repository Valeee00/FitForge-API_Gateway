package br.fitforge.gateway.bff.dto;

/**
 * Sessão + situação atual da ficha. Como cada serviço tem seu banco, a ficha
 * pode ter sido excluída ou renomeada depois — o BFF resolve isso para a tela.
 */
public record SessaoEnriquecidaDTO(SessionDTO sessao, boolean fichaExiste, String nomeAtualDaFicha) {}
