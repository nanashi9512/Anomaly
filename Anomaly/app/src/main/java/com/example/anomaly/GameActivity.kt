package com.example.anomaly

import android.graphics.Color
import android.os.Bundle
import android.view.animation.AlphaAnimation
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.anomaly.databinding.ActivityGameBinding
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    private var winDialog: AlertDialog? = null
    private var gameOverDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Rule: Hide status bar (wifi, battery, time) and navigation bar
        hideSystemUI()

        binding.sceneView.setCallbacks(
            onCorrect = { itemName -> viewModel.onSceneTap(itemName) },
            onWrong = { viewModel.onWrongTap() }
        )

        // Navigation between stages. 
        // Rule: Buttons must NEVER reflect progress (never disable/hide).
        binding.btnBedroom.setOnClickListener { viewModel.switchStage(StageId.BEDROOM) }
        binding.btnKitchen.setOnClickListener { viewModel.switchStage(StageId.KITCHEN) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateUI(state: GameState) {
        val stage = if (state.currentStage == StageId.BEDROOM) viewModel.bedroomStage else viewModel.kitchenStage
        
        // Stage Switching Crossfade (250ms)
        if (binding.sceneView.tag != state.currentStage) {
            val fade = AlphaAnimation(0f, 1f).apply { duration = 250 }
            binding.sceneView.startAnimation(fade)
            binding.sceneView.tag = state.currentStage
        }

        binding.sceneView.setData(stage, state.itemsToDraw)
        
        // Hide Found X/N progress. Show only global wrong attempts.
        binding.wrongAttemptsText.text = getString(R.string.misses_format, state.wrongAttempts)

        // Timer Logic
        binding.timerText.text = state.timeLeft.toString()
        if (state.timeLeft <= 20) {
            // Blink: Red on even seconds, White on odd seconds
            binding.timerText.setTextColor(if (state.timeLeft % 2 == 0) Color.RED else Color.WHITE)
        } else {
            binding.timerText.setTextColor(Color.WHITE)
        }

        // Win and GameOver Dialogs
        if (state.isWon) {
            if (winDialog == null || !winDialog!!.isShowing) {
                showWinDialog(state.wrongAttempts)
            }
        } else if (state.isTimeOut) {
            if (gameOverDialog == null || !gameOverDialog!!.isShowing) {
                showGameOverDialog()
            }
        } else {
            // Dismiss dialogs if game is restarted
            winDialog?.dismiss()
            gameOverDialog?.dismiss()
        }
    }

    private fun showWinDialog(misses: Int) {
        winDialog = AlertDialog.Builder(this)
            .setTitle(R.string.win_title)
            .setMessage(getString(R.string.win_message, misses))
            .setCancelable(false)
            .setPositiveButton(R.string.play_again) { _, _ -> viewModel.startNewGame() }
            .setNegativeButton(R.string.main_menu) { _, _ -> finish() }
            .create()
        winDialog?.show()
    }

    private fun showGameOverDialog() {
        gameOverDialog = AlertDialog.Builder(this)
            .setTitle(R.string.game_over)
            .setMessage(R.string.timeout_message)
            .setCancelable(false)
            .setPositiveButton(R.string.retry) { _, _ -> viewModel.startNewGame() }
            .setNegativeButton(R.string.main_menu) { _, _ -> finish() }
            .create()
        gameOverDialog?.show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI() // Re-hide after returning from dialogs
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.sceneView.release()
    }
}
