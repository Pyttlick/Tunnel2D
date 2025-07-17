package com.example.tunnel2d

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tunnelView = findViewById<TunnelView>(R.id.tunnelView)
        val joystick = findViewById<JoystickView>(R.id.joystick)

        joystick.setListener(object : JoystickView.Listener {
            override fun onPausePressed() {
                tunnelView.togglePause()
            }
            override fun shouldShowPauseText(): Boolean {
                return tunnelView.isPaused()
            }

        })
    }
}
