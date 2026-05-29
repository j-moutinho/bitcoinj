package org.bitcoinj.core;

import java.time.Instant;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Difficulty;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import static org.junit.Assert.assertThrows;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandardDifficultyTransitionsTest {
    private static final DifficultyTransitions TRANSITIONS = DifficultyTransitions.of(BitcoinNetwork.MAINNET);
    private StandardDifficultyTransitions standardTransitions;
    private BlockStore mockBlockStore;
    private StoredBlock mockStoredPrev;
    private Block mockNextBlock;
    private Block mockPrevHeader;

    private static final int TARGET_TIMESPAN = 1209600;
    private static final int L_INF = TARGET_TIMESPAN / 4;
    private static final int L_SUP = TARGET_TIMESPAN * 4;

    @Before
    public void setUp() {
        standardTransitions = new StandardDifficultyTransitions(BitcoinNetwork.MAINNET);
        mockBlockStore = mock(BlockStore.class);
        mockStoredPrev = mock(StoredBlock.class);
        mockNextBlock = mock(Block.class);
        mockPrevHeader = mock(Block.class);
        when(mockStoredPrev.getHeader()).thenReturn(mockPrevHeader);
    }

    // weak and strong equivalence

    @Test
    public void testTC1_NoTransition_TargetMatches() throws Exception {
        when(mockStoredPrev.getHeight()).thenReturn(2014); // CE1
        Difficulty mockDiff = mock(Difficulty.class);
        when(mockPrevHeader.difficultyTarget()).thenReturn(mockDiff);
        when(mockNextBlock.difficultyTarget()).thenReturn(mockDiff); // CE3
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testTC2_NoTransition_TargetMismatch() {
        when(mockStoredPrev.getHeight()).thenReturn(2014);
        Difficulty diff1 = mock(Difficulty.class);
        Difficulty diff2 = mock(Difficulty.class);
        when(mockPrevHeader.difficultyTarget()).thenReturn(diff1);
        when(mockNextBlock.difficultyTarget()).thenReturn(diff2); // CE4
        assertThrows(VerificationException.class, () ->
                standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore));
    }

    @Test
    public void testTC3_Transition_TooShort_Matches() throws Exception {
        setupTransitionPoint(L_INF - 100); // CE5
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testTC7_Transition_TooShort_Mismatch() throws BlockStoreException {
        setupTransitionPoint(L_INF - 100);
        when(mockNextBlock.difficultyTarget()).thenReturn(mock(Difficulty.class)); // CE9
        assertThrows(VerificationException.class, () ->
                standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore));
    }

    @Test
    public void testTC4_Transition_Normal_Matches() throws Exception {
        setupTransitionPoint(TARGET_TIMESPAN); // CE6
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testTC6_Transition_Normal_Mismatch() throws BlockStoreException {
        setupTransitionPoint(TARGET_TIMESPAN);
        when(mockNextBlock.difficultyTarget()).thenReturn(mock(Difficulty.class)); // CE9
        assertThrows(VerificationException.class, () ->
                standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore));
    }

    @Test
    public void testTC5_Transition_TooLong_Matches() throws Exception {
        setupTransitionPoint(L_SUP + 100); // CE7
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testTC8_Transition_TooLong_Mismatch() throws BlockStoreException {
        setupTransitionPoint(L_SUP + 100);
        when(mockNextBlock.difficultyTarget()).thenReturn(mock(Difficulty.class)); // CE9
        assertThrows(VerificationException.class, () ->
                standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore));
    }

    // boundary value analysis

    @Test
    public void testBV1_MinBoundary() throws Exception {
        setupTransitionPoint(L_INF);
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testBV2_AboveMinBoundary() throws Exception {
        setupTransitionPoint(L_INF + 1);
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testBV3_Nominal() throws Exception {
        setupTransitionPoint(TARGET_TIMESPAN);
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testBV4_BelowMaxBoundary() throws Exception {
        setupTransitionPoint(L_SUP - 1);
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    @Test
    public void testBV5_MaxBoundary() throws Exception {
        setupTransitionPoint(L_SUP);
        standardTransitions.checkDifficultyTransitions(mockStoredPrev, mockNextBlock, mockBlockStore);
    }

    private void setupTransitionPoint(int timespanSeconds) throws BlockStoreException {
        when(mockStoredPrev.getHeight()).thenReturn(2015);
        when(mockPrevHeader.getHash()).thenReturn(mock(Sha256Hash.class));
        StoredBlock mockCursor = mock(StoredBlock.class);
        Block mockCursorHeader = mock(Block.class);
        when(mockCursor.getHeader()).thenReturn(mockCursorHeader);
        when(mockCursorHeader.prevHash()).thenReturn(mock(Sha256Hash.class));
        when(mockCursor.getHeight()).thenReturn(0);
        when(mockBlockStore.get(any())).thenReturn(mockCursor);
        when(mockCursorHeader.time()).thenReturn(Instant.ofEpochSecond(0));
        when(mockPrevHeader.time()).thenReturn(Instant.ofEpochSecond(timespanSeconds));
        Difficulty mockPrevTarget = mock(Difficulty.class);
        Difficulty mockNewTarget = mock(Difficulty.class);
        when(mockPrevHeader.difficultyTarget()).thenReturn(mockPrevTarget);
        when(mockPrevTarget.adjust(any(), any(), any())).thenReturn(mockNewTarget);
        when(mockNextBlock.difficultyTarget()).thenReturn(mockNewTarget);
    }
}