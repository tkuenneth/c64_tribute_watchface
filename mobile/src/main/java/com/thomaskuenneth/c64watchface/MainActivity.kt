package com.thomaskuenneth.c64watchface

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.thomaskuenneth.c64watchface.databinding.MainBinding
import com.thomaskuenneth.common.C64
import java.util.concurrent.Callable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainBinding
    private lateinit var c64: C64

    private var shouldBeRunning = false
    private val callback = Callable {
        if (shouldBeRunning)
            binding.c64watchface.invalidate()
        shouldBeRunning
    }
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            c64.setup()
            binding.c64watchface.postInvalidate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        c64 = C64(this, PreferenceManager.getDefaultSharedPreferences(this))
        c64.centerHorizontally = false
        c64.setup()
        binding.c64watchface.tag = c64
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, windowInsets ->
            if (windowInsets.isVisible(WindowInsetsCompat.Type.statusBars()))
                supportActionBar?.show()
            else
                supportActionBar?.hide()
            windowInsets
        }
        binding.root.setOnClickListener {
            hideSystemBars()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            hideSystemBars()
        }, 3000L)
    }

    override fun onPause() {
        super.onPause()
        shouldBeRunning = false
    }

    override fun onResume() {
        super.onResume()
        shouldBeRunning = true
        c64.pulse(callback)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options, menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                launcher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.info -> {
                startActivity(Intent(this, InfoActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun hideSystemBars() {
        with(WindowCompat.getInsetsController(window, window.decorView)) {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}