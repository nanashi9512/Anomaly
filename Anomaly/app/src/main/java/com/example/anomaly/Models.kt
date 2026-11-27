package com.example.anomaly

import android.graphics.PorterDuff

enum class StageId { BEDROOM, KITCHEN }
enum class ItemGroup { REGULAR, SPECIAL }

data class GameItem(
    val name: String,
    val normalRes: Int?,
    val diffRes: Int,
    val group: ItemGroup
)

data class Stage(
    val id: StageId,
    val bgRes: Int,
    val below: List<Int>,
    val items: List<GameItem>,
    val above: List<Int>,
    val aboveAlpha: Map<Int, Float> = emptyMap(),
    val aboveBlendMode: Map<Int, PorterDuff.Mode> = emptyMap()
)

data class ItemDrawState(
    val name: String,
    val resId: Int,
    val isFound: Boolean
)

data class GameState(
    val currentStage: StageId = StageId.BEDROOM,
    val itemsToDraw: List<ItemDrawState> = emptyList(),
    val wrongAttempts: Int = 0,
    val timeLeft: Int = 60,
    val isWon: Boolean = false,
    val isTimeOut: Boolean = false
)
