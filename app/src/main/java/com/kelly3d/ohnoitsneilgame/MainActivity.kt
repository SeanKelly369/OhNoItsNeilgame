package com.kelly3d.ohnoitsneilgame

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Hosts the remastered Amiga world and touchscreen controller overlay. */
class MainActivity : ComponentActivity() {
    private lateinit var gameView: OriginalGameView
    private lateinit var controllerView: TouchControllerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        gameView = OriginalGameView(this)
        controllerView = TouchControllerView(this)

        controllerView.listener = object : TouchControllerView.Listener {
            override fun onMoveChanged(direction: Int, climb: Int) {
                gameView.setControllerMovement(direction, climb)
            }
            override fun onJumpPressed() = gameView.pressJump()
            override fun onUsePressed() = gameView.pressUse()
            override fun onUpPressed() = gameView.pressUpAction()
            override fun onMapPressed() = gameView.toggleMap()
        }

        gameView.onControlsStateChanged = { enabled, doorLabel ->
            controllerView.controlsEnabled = enabled
            controllerView.nearbyDoorLabel = doorLabel
        }
        controllerView.controlsEnabled = gameView.controlsEnabled
        controllerView.nearbyDoorLabel = if (gameView.controlsEnabled) gameView.game.nearbyDoor()?.label else null

        val root = FrameLayout(this).apply {
            addView(gameView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(controllerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        setContentView(root)
        gameView.requestFocus()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            gameView.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            controllerView.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        controllerView.clearInputs()
        gameView.pause()
        super.onPause()
    }
}
