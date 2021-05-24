package com.thomaskuenneth.c64watchface

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.thomaskuenneth.c64watchface.databinding.MainBinding
import com.thomaskuenneth.common.C64
import java.util.concurrent.Callable

private const val REQUEST_CODE_PREFS = 123

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainBinding
    private lateinit var c64: C64

    private var shouldBeRunning = false
    private val callback = Callable {
        if (shouldBeRunning)
            binding.c64watchface.invalidate()
        shouldBeRunning
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        c64 = C64(this, PreferenceManager.getDefaultSharedPreferences(this))
        c64.centerHorizontally = false
        c64.setup()
        binding.c64watchface.tag = c64
        setContentView(binding.root)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PREFS) {
            c64.setup()
            binding.c64watchface.postInvalidate()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options, menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, REQUEST_CODE_PREFS)
                true
            }
            R.id.info -> {
                val intent = Intent(this, InfoActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}