package com.thomaskuenneth.c64watchface

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thomaskuenneth.c64watchface.databinding.InfoBinding

class InfoActivity : AppCompatActivity() {

    private lateinit var binding: InfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding = InfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}