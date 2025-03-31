package com.example.example

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Random

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var selectedUri: Uri? = null
    private var equalizer: Equalizer? = null
    private lateinit var btnSelect: Button
    private lateinit var txtFileName: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button
    private lateinit var btnEqualizer: Button
    private lateinit var btnRandom: Button
    private lateinit var btnDarkMode: Button
    private lateinit var btnLoop: Button
    private lateinit var eqBandsContainer: LinearLayout
    private lateinit var seekBarProgress: SeekBar

    private var isRandomMode = false
    private var isLooping = false
    private val random = Random()
    private val progressHandler = Handler()
    private val randomRunnable = object : Runnable {
        override fun run() {
            if (isRandomMode && mediaPlayer?.isPlaying == true) {
                updateRandomBands()
                eqBandsContainer.postDelayed(this, 200)
            }
        }
    }
    private val updateProgressTask = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupButtonListeners()
        applyDarkModePreference()
    }

    private fun initializeViews() {
        btnSelect = findViewById(R.id.btnSelect)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnEqualizer = findViewById(R.id.btnEqualizer)
        btnRandom = findViewById(R.id.btnRandom)
        btnDarkMode = findViewById(R.id.btnDarkMode)
        btnLoop = findViewById(R.id.btnLoop)
        txtFileName = findViewById(R.id.txtFileName)
        eqBandsContainer = findViewById(R.id.eqBandsContainer)
        seekBarProgress = findViewById(R.id.seekBarProgress)
    }

    private fun setupButtonListeners() {
        btnSelect.setOnClickListener { openFilePicker() }
        btnPlay.setOnClickListener { playSelectedFile() }
        btnPause.setOnClickListener { pauseAudio() }
        btnStop.setOnClickListener { stopAudio() }
        btnEqualizer.setOnClickListener { toggleEqualizer() }
        btnRandom.setOnClickListener { toggleRandomMode() }
        btnDarkMode.setOnClickListener { toggleDarkMode() }
        btnLoop.setOnClickListener { toggleLoop() }

        seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedUri = uri
                    txtFileName.text = getFileName(uri)
                    btnPlay.isEnabled = true
                    btnEqualizer.isEnabled = true
                    btnPause.isEnabled = false
                    btnStop.isEnabled = false
                    btnLoop.isEnabled = true
                }
            }
        }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun toggleDarkMode() {
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        val newMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES

        AppCompatDelegate.setDefaultNightMode(newMode)
        sharedPreferences.edit().putBoolean("dark_mode", !isDarkMode).apply()
    }

    private fun applyDarkModePreference() {
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                return it.getString(index)
            }
        }
        return "Unknown file"
    }

    private fun playSelectedFile() {
        selectedUri?.let { uri ->
            try {
                mediaPlayer?.release()
                equalizer?.release()
                stopRandomEffect()
                progressHandler.removeCallbacks(updateProgressTask)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    setOnPreparedListener { mp ->
                        btnPlay.isEnabled = false
                        btnPause.isEnabled = true
                        btnStop.isEnabled = true
                        seekBarProgress.max = duration
                        mp.start()
                        setupEqualizer()
                        progressHandler.post(updateProgressTask)
                    }
                    setOnCompletionListener {
                        if (!isLooping) {
                            btnPlay.isEnabled = true
                            btnPause.isEnabled = false
                            btnStop.isEnabled = false
                            btnPause.text = "Pause"
                            stopRandomEffect()
                        }
                    }
                    isLooping = this@MainActivity.isLooping
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error playing file", e)
                txtFileName.text = "Error playing file"
            }
        }
    }

    private fun toggleLoop() {
        isLooping = !isLooping
        mediaPlayer?.isLooping = isLooping
        btnLoop.text = if (isLooping) "Loop: ON" else "Loop: OFF"
    }

    private fun updateProgress() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                seekBarProgress.progress = mp.currentPosition
            }
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                btnPause.text = "Resume"
                stopRandomEffect()
                progressHandler.removeCallbacks(updateProgressTask)
            } else {
                it.start()
                btnPause.text = "Pause"
                if (isRandomMode) startRandomEffect()
                progressHandler.post(updateProgressTask)
            }
        }
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            it.stop()
            btnPlay.isEnabled = true
            btnPause.isEnabled = false
            btnStop.isEnabled = false
            btnPause.text = "Pause"
            eqBandsContainer.visibility = LinearLayout.GONE
            seekBarProgress.progress = 0
            stopRandomEffect()
            progressHandler.removeCallbacks(updateProgressTask)
            isRandomMode = false
            btnRandom.text = "Random Mode"
            try {
                it.prepare()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing after stop", e)
            }
        }
    }

    private fun setupEqualizer() {
        mediaPlayer?.let { mp ->
            try {
                equalizer = Equalizer(0, mp.audioSessionId).apply {
                    enabled = true
                    eqBandsContainer.removeAllViews()

                    // Add title
                    TextView(this@MainActivity).apply {
                        text = "Equalizer Bands (${numberOfBands + 4} total)"
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 8.dpToPx() }
                    }.also { eqBandsContainer.addView(it) }

                    // Real bands
                    val minLevel = bandLevelRange[0]
                    val maxLevel = bandLevelRange[1]
                    val levelRange = maxLevel - minLevel

                    for (i in 0 until numberOfBands) {
                        addEqualizerBand(i.toShort(), minLevel.toInt(), maxLevel.toInt(), levelRange)
                    }

                    // Virtual bands
                    addVirtualBands(minLevel.toInt(), maxLevel.toInt(), levelRange)
                }
                btnRandom.isEnabled = true
            } catch (e: Exception) {
                Log.e("Equalizer", "Setup error", e)
                txtFileName.text = "Equalizer error: ${e.localizedMessage}"
            }
        }
    }

    private fun addEqualizerBand(band: Short, minLevel: Int, maxLevel: Int, levelRange: Int) {
        equalizer?.let { eq ->
            val bandLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4.dpToPx(), 0, 4.dpToPx()) }
            }

            // Frequency label
            TextView(this).apply {
                text = "${eq.getCenterFreq(band) / 1000}Hz"
                layoutParams = LinearLayout.LayoutParams(80.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }.also { bandLayout.addView(it) }

            // SeekBar
            SeekBar(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                max = levelRange
                progress = eq.getBandLevel(band) - minLevel

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) eq.setBandLevel(band, (progress + minLevel).toShort())
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }.also { bandLayout.addView(it) }

            eqBandsContainer.addView(bandLayout)
        }
    }

    private fun addVirtualBands(minLevel: Int, maxLevel: Int, levelRange: Int) {
        val virtualFreqs = listOf("800Hz", "1.2kHz", "3.5kHz", "6kHz")

        virtualFreqs.forEachIndexed { i, freq ->
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4.dpToPx(), 0, 4.dpToPx()) }
                tag = "VIRTUAL_$i"

                // Virtual band label
                TextView(this@MainActivity).apply {
                    text = "Virtual $freq"
                    layoutParams = LinearLayout.LayoutParams(120.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }.also { addView(it) }

                // Virtual seekbar
                SeekBar(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    max = levelRange
                    progress = levelRange / 2

                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) updateNearbyBands(progress + minLevel)
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }.also { addView(it) }
            }.also { eqBandsContainer.addView(it) }
        }
    }

    private fun toggleEqualizer() {
        if (eqBandsContainer.visibility == LinearLayout.VISIBLE) {
            eqBandsContainer.visibility = LinearLayout.GONE
            stopRandomEffect()
        } else if (mediaPlayer?.isPlaying == true) {
            eqBandsContainer.visibility = LinearLayout.VISIBLE
            eqBandsContainer.post { eqBandsContainer.requestLayout() }
        }
    }

    private fun toggleRandomMode() {
        isRandomMode = !isRandomMode
        btnRandom.text = if (isRandomMode) "Stop Random" else "Random Mode"
        if (isRandomMode) startRandomEffect() else stopRandomEffect()
    }

    private fun startRandomEffect() {
        if (eqBandsContainer.visibility == LinearLayout.VISIBLE) {
            eqBandsContainer.post(randomRunnable)
        }
    }

    private fun stopRandomEffect() {
        eqBandsContainer.removeCallbacks(randomRunnable)
    }

    private fun updateRandomBands() {
        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]

            // Update real bands
            for (i in 0 until eq.numberOfBands) {
                val band = i.toShort()
                val change = when (random.nextInt(3)) {
                    0 -> random.nextInt(400) - 200
                    1 -> random.nextInt(200) - 100
                    else -> random.nextInt(100) - 50
                }
                val newLevel = (eq.getBandLevel(band) + change).coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(band, newLevel.toShort())
                updateSeekBarForBand(i, newLevel - minLevel)
            }

            // Update virtual bands
            for (i in 0 until eqBandsContainer.childCount) {
                (eqBandsContainer.getChildAt(i) as? LinearLayout)?.let { layout ->
                    if (layout.tag?.toString()?.startsWith("VIRTUAL") == true) {
                        (layout.getChildAt(1) as? SeekBar)?.let { seekBar ->
                            val change = random.nextInt(50) - 25
                            seekBar.progress = (seekBar.progress + change).coerceIn(0, seekBar.max)
                        }
                    }
                }
            }
        }
    }

    private fun updateNearbyBands(targetLevel: Int) {
        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val bandCount = eq.numberOfBands
            if (bandCount >= 2) {
                val band1 = (bandCount * 1 / 4).toShort()
                val band2 = (bandCount * 3 / 4).toShort()

                eq.setBandLevel(band1, (targetLevel * 0.7).toInt().toShort())
                eq.setBandLevel(band2, (targetLevel * 0.5).toInt().toShort())

                updateSeekBarForBand(band1.toInt(), (targetLevel * 0.7).toInt() - minLevel)
                updateSeekBarForBand(band2.toInt(), (targetLevel * 0.5).toInt() - minLevel)
            }
        }
    }

    private fun updateSeekBarForBand(bandIndex: Int, progress: Int) {
        val viewIndex = bandIndex + 1 // +1 per saltare il titolo
        if (viewIndex < eqBandsContainer.childCount) {
            ((eqBandsContainer.getChildAt(viewIndex) as? LinearLayout)?.getChildAt(1)?.let {
                (it as? SeekBar)?.progress = progress
            })
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopRandomEffect()
        progressHandler.removeCallbacks(updateProgressTask)
        mediaPlayer?.release()
        equalizer?.release()
        super.onDestroy()
    }
}