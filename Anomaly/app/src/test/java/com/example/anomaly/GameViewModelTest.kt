package com.example.anomaly

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameViewModelTest {

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = GameViewModel(app)
    }

    @Test
    fun testStartNewGameRandomization() {
        val flagCombinations = mutableSetOf<Pair<Boolean, Boolean>>()
        var lastState: GameState? = null

        repeat(1000) {
            viewModel.startNewGame()
            val state = viewModel.state.value
            
            // 1. Bedroom randomize regular items (2-3)
            val bedroomOdds = state.oddByStage[StageId.BEDROOM] ?: emptySet()
            val bRegCount = bedroomOdds.count { it !in listOf("underbed", "balcony") }
            assertTrue("Bedroom regular odds count: $bRegCount", bRegCount in 2..3)
            
            // 2. Kitchen randomize regular items (2-3)
            val kitchenOdds = state.oddByStage[StageId.KITCHEN] ?: emptySet()
            assertTrue("Kitchen odds count: ${kitchenOdds.size}", kitchenOdds.size in 2..3)
            
            // 3. Flags tracking (ensure variety)
            val hasUnderbed = "underbed" in bedroomOdds
            val hasBalcony = "balcony" in bedroomOdds
            flagCombinations.add(hasUnderbed to hasBalcony)
            
            // 4. Hidden Total Verification
            val expectedTotal = bRegCount + (if (hasUnderbed) 1 else 0) + (if (hasBalcony) 1 else 0) + kitchenOdds.size
            assertEquals("Total mismatch", expectedTotal, state.totalDifference)
            assertTrue("Total out of range", state.totalDifference in 4..8)
            
            // 5. Uniqueness check
            if (lastState != null) {
                val currentOdds = bedroomOdds to kitchenOdds
                val lastOdds = lastState!!.oddByStage[StageId.BEDROOM] to lastState!!.oddByStage[StageId.KITCHEN]
                assertNotEquals("Consecutive identical games", lastOdds, currentOdds)
            }
            lastState = state
        }
        
        assertEquals("Not all underbed/balcony combinations appeared", 4, flagCombinations.size)
    }
}
