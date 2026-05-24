package org.bitcoinj.testing;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.FullPrunedBlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes de Caixa Negra (Equivalence Classes e BVA) para o método connectTransactions
 * focando na validação da variável valueOut.
 */
public class FullPrunedBlockChainTest {

    private FullPrunedBlockChain blockChain;
    private NetworkParameters params;

    @Before
    public void setUp() throws Exception {
        // Usamos os parâmetros de Unit Test para isolar a rede
        params = UnitTestParams.get();

        // Fazemos mock da base de dados para não termos de criar ficheiros físicos
        FullPrunedBlockStore mockStore = Mockito.mock(FullPrunedBlockStore.class);

        // Simular que a base de dados tem os cabeçalhos necessários
        StoredBlock mockChainHead = Mockito.mock(StoredBlock.class);
        when(mockStore.getVerifiedChainHead()).thenReturn(mockChainHead);
        when(mockChainHead.getHeight()).thenReturn(100);

        // Instanciamos a blockchain a ser testada
        blockChain = new FullPrunedBlockChain(params, mockStore);
        blockChain.setRunScripts(false); // Desativar a verificação de scripts (P2SH) para simplificar o teste
    }

    /**
     * Função auxiliar para criar um Bloco falso com uma transação que tem um output específico.
     */
    private Block createMockBlockWithOutput(Coin valueOut) {
        Block block = new Block(params, 2, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, 1, 1, 1, Collections.emptyList());

        Transaction tx = new Transaction(params);
        // Criar um input coinbase para evitar verificações de inputs anteriores na base de dados
        tx.addInput(new TransactionInput(params, tx, new byte[]{}));

        // Adicionar o output com o valor que queremos testar
        tx.addOutput(valueOut, new org.bitcoinj.script.Script(new byte[0]));

        block.addTransaction(tx);
        return block;
    }

    // =======================================================================
    // T01: BVA Robusto / Classe de Equivalência Inválida 1 (valueOut < 0)
    // =======================================================================
    @Test(expected = VerificationException.class)
    public void testConnectTransactions_InvalidNegativeValue() throws Exception {
        Coin negativeValue = Coin.valueOf(-1);
        Block block = createMockBlockWithOutput(negativeValue);

        // Deve lançar VerificationException devido a: valueOut.signum() < 0
        blockChain.connectTransactions(101, block);
    }

    // =======================================================================
    // T02: BVA Simples - Limite Inferior (valueOut == 0)
    // =======================================================================
    @Test
    public void testConnectTransactions_LowerBoundZero() {
        Coin zeroValue = Coin.ZERO;
        Block block = createMockBlockWithOutput(zeroValue);

        try {
            blockChain.connectTransactions(101, block);
            // Se chegar aqui sem lançar VerificationException relacionada com o limite, o teste passa.
        } catch (VerificationException e) {
            if (e.getMessage().contains("out of range")) {
                fail("Não deveria lançar VerificationException para valueOut == 0");
            }
        } catch (BlockStoreException e) {
            // Ignorar erros de base de dados mockada, queremos apenas validar a lógica do valueOut
        }
    }

    // =======================================================================
    // T03: BVA Simples - Valor Nominal (0 < valueOut < MAX_MONEY)
    // =======================================================================
    @Test
    public void testConnectTransactions_ValidNominalValue() {
        Coin nominalValue = Coin.COIN; // 1 Bitcoin
        Block block = createMockBlockWithOutput(nominalValue);

        try {
            blockChain.connectTransactions(101, block);
        } catch (VerificationException e) {
            if (e.getMessage().contains("out of range")) {
                fail("Não deveria lançar VerificationException para valor válido");
            }
        } catch (BlockStoreException e) {
            // Ignorar
        }
    }

    // =======================================================================
    // T04: BVA Simples - Limite Superior (valueOut == MAX_MONEY)
    // =======================================================================
    @Test
    public void testConnectTransactions_UpperBoundMaxMoney() {
        Coin maxMoney = params.network().maxMoney();
        Block block = createMockBlockWithOutput(maxMoney);

        try {
            blockChain.connectTransactions(101, block);
        } catch (VerificationException e) {
            if (e.getMessage().contains("out of range")) {
                fail("Não deveria lançar VerificationException para valueOut == MAX_MONEY");
            }
        } catch (BlockStoreException e) {
            // Ignorar
        }
    }

    // =======================================================================
    // T05: BVA Robusto / Classe de Equivalência Inválida 2 (valueOut > MAX_MONEY)
    // =======================================================================
    @Test(expected = VerificationException.class)
    public void testConnectTransactions_InvalidExceedsMaxMoney() throws Exception {
        Coin exceededMoney = params.network().maxMoney().add(Coin.SATOSHI);
        Block block = createMockBlockWithOutput(exceededMoney);

        // Deve lançar VerificationException devido a: params.network().exceedsMaxMoney(valueOut)
        blockChain.connectTransactions(101, block);
    }
}
