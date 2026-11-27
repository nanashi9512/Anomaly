package com.example.anomaly

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var previousCombination: String? = null
    
    private var oddByStage: Map<StageId, Set<String>> = emptyMap()
    private var foundByStage: Map<StageId, Set<String>> = emptyMap()
    private var totalDifference: Int = 0
    private var timerJob: Job? = null

    val bedroomStage: Stage
    val kitchenStage: Stage
    
    init {
        bedroomStage = createStage(StageId.BEDROOM, "bedroom", 
            listOf("poster", "vase", "calendar", "doll"), 
            listOf("underbed", "balcony"),
            below = emptyList(), above = emptyList()
        )
        
        kitchenStage = createStage(StageId.KITCHEN, "kitchen",
            listOf("chicken", "food", "frame", "heart", "lamp", "owl"),
            emptyList(),
            below = emptyList(), above = listOf("kitchen_shadow")
        )
        
        startNewGame()
    }

    private fun createStage(id: StageId, prefix: String, reg: List<String>, spec: List<String>, below: List<String>, above: List<String>): Stage {
        val pkg = getApplication<Application>().packageName
        val res = getApplication<Application>().resources
        val bgRes = res.getIdentifier("${prefix}_bg", "drawable", pkg)
        
        val items = reg.map { name ->
            val n = res.getIdentifier("${prefix}_${name}_n", "drawable", pkg)
            val a = res.getIdentifier("${prefix}_${name}_a", "drawable", pkg)
            GameItem(name, if (n != 0) n else null, a, ItemGroup.REGULAR)
        } + spec.map { name ->
            val a = res.getIdentifier("${prefix}_${name}_a", "drawable", pkg)
            GameItem(name, null, a, ItemGroup.SPECIAL)
        }
        
        val alphaMap = mutableMapOf<Int, Float>()
        val aboveIds = above.map { 
            val idRes = res.getIdentifier(it, "drawable", pkg)
            if (it == "kitchen_shadow") alphaMap[idRes] = 0.35f
            idRes
        }

        return Stage(id, bgRes, below.map { res.getIdentifier(it, "drawable", pkg) }, items, aboveIds, alphaMap)
    }

    fun startNewGame() {
        var combo: String
        var bOdds: Set<String>
        var kOdds: Set<String>

        do {
            val bNum = Random.nextInt(2, 4)
            val bReg = bedroomStage.items.filter { it.group == ItemGroup.REGULAR }.shuffled().take(bNum).map { it.name }
            val uFlg = Random.nextBoolean(); val lFlg = Random.nextBoolean()
            bOdds = (bReg + (if (uFlg) listOf("underbed") else emptyList()) + (if (lFlg) listOf("balcony") else emptyList())).toSet()
            val kNum = Random.nextInt(2, 4)
            kOdds = kitchenStage.items.shuffled().take(kNum).map { it.name }.toSet()
            combo = "${bOdds.sorted()}|${kOdds.sorted()}"
        } while (combo == previousCombination)

        previousCombination = combo
        oddByStage = mapOf(StageId.BEDROOM to bOdds, StageId.KITCHEN to kOdds)
        foundByStage = mapOf(StageId.BEDROOM to emptySet(), StageId.KITCHEN to emptySet())
        totalDifference = bOdds.size + kOdds.size
        
        startTimer()
        updateDisplayState(StageId.BEDROOM, 0, false, 60, false)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var timeLeft = 60
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
                if (_state.value.isWon) break
                _state.value = _state.value.copy(timeLeft = timeLeft, isTimeOut = timeLeft == 0)
            }
        }
    }

    fun switchStage(id: StageId) {
        val s = _state.value
        updateDisplayState(id, s.wrongAttempts, s.isWon, s.timeLeft, s.isTimeOut)
    }

    private fun updateDisplayState(stageId: StageId, misses: Int, won: Boolean, timeLeft: Int, timeOut: Boolean) {
        val stage = if (stageId == StageId.BEDROOM) bedroomStage else kitchenStage
        val drawStates = stage.items.mapNotNull { item ->
            val isOdd = item.name in (oddByStage[stageId] ?: emptySet())
            val isFound = item.name in (foundByStage[stageId] ?: emptySet())
            val resId = if (isFound || isOdd) item.diffRes else item.normalRes
            resId?.let { ItemDrawState(item.name, it, isFound) }
        }
        _state.value = GameState(stageId, drawStates, misses, timeLeft, won, timeOut)
        if (won || timeOut) timerJob?.cancel()
    }

    fun onSceneTap(itemName: String) {
        val s = _state.value
        if (s.isWon || s.isTimeOut) return
        val stageId = s.currentStage
        val odds = oddByStage[stageId] ?: emptySet()
        val found = foundByStage[stageId] ?: emptySet()

        if (itemName in odds && itemName !in found) {
            val newFoundMap = foundByStage.toMutableMap().apply { this[stageId] = found + itemName }
            foundByStage = newFoundMap
            val totalFound = foundByStage.values.sumOf { it.size }
            updateDisplayState(stageId, s.wrongAttempts, totalFound == totalDifference, s.timeLeft, s.isTimeOut)
        } else if (itemName !in found) {
            updateDisplayState(stageId, s.wrongAttempts + 1, s.isWon, s.timeLeft, s.isTimeOut)
        }
    }
    
    fun onWrongTap() {
        if (!_state.value.isWon && !_state.value.isTimeOut) {
            updateDisplayState(_state.value.currentStage, _state.value.wrongAttempts + 1, false, _state.value.timeLeft, false)
        }
    }

    override fun onCleared() {
        super.onCleared(); timerJob?.cancel()
    }
}
