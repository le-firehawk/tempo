package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.EqualizerManager
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.util.Preferences

class EqualizerFragment : Fragment() {

    private var equalizerManager: EqualizerManager? = null
    private lateinit var eqBandsContainer: LinearLayout
    private lateinit var eqSwitch: Switch
    private lateinit var resetButton: Button
    private lateinit var safeSpace: Space
    private val bandSeekBars = mutableListOf<SeekBar>()

    private val connection = object : ServiceConnection {
        @OptIn(UnstableApi::class)
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MediaService.LocalBinder
            equalizerManager = binder.getEqualizerManager()
            initUI()
            restoreEqualizerPreferences()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            equalizerManager = null
        }
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        Intent(requireContext(), MediaService::class.java).also { intent ->
            intent.action = MediaService.ACTION_BIND_EQUALIZER
            requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unbindService(connection)
        equalizerManager = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eqBandsContainer = view.findViewById(R.id.eq_bands_container)
        eqSwitch = view.findViewById(R.id.equalizer_switch)
        resetButton = view.findViewById(R.id.equalizer_reset_button)
        safeSpace = view.findViewById(R.id.equalizer_bottom_space)
    }

    private fun initUI() {
        val manager = equalizerManager
        val notSupportedView = view?.findViewById<LinearLayout>(R.id.equalizer_not_supported_container)
        val switchRow = view?.findViewById<View>(R.id.equalizer_switch_row)

        if (manager == null || manager.getNumberOfBands().toInt() == 0) {
            switchRow?.visibility = View.GONE
            resetButton.visibility = View.GONE
            eqBandsContainer.visibility = View.GONE
            safeSpace.visibility = View.GONE
            notSupportedView?.visibility = View.VISIBLE
            return
        }

        notSupportedView?.visibility = View.GONE
        switchRow?.visibility = View.VISIBLE
        resetButton.visibility = View.VISIBLE
        eqBandsContainer.visibility = View.VISIBLE
        safeSpace.visibility = View.VISIBLE

        eqSwitch.setOnCheckedChangeListener(null)
        eqSwitch.isChecked = Preferences.isEqualizerEnabled()
        updateUiEnabledState(eqSwitch.isChecked)
        eqSwitch.setOnCheckedChangeListener { _, isChecked ->
            manager.setEnabled(isChecked)
            Preferences.setEqualizerEnabled(isChecked)
            updateUiEnabledState(isChecked)
        }

        createBandSliders()

        resetButton.setOnClickListener {
            resetEqualizer()
            saveBandLevelsToPreferences()
        }
    }

    private fun updateUiEnabledState(isEnabled: Boolean) {
        resetButton.isEnabled = isEnabled
        bandSeekBars.forEach { it.isEnabled = isEnabled }
    }

    private fun createBandSliders() {
        val manager = equalizerManager ?: return
        eqBandsContainer.removeAllViews()
        bandSeekBars.clear()
        val bands = manager.getNumberOfBands()
        val bandLevelRange = manager.getBandLevelRange() ?: shortArrayOf(-1500, 1500)
        val minLevel = bandLevelRange[0].toInt()
        val maxLevel = bandLevelRange[1].toInt()

        val savedLevels = Preferences.getEqualizerBandLevels(bands)
        for (i in 0 until bands) {
            val band = i.toShort()
            val freq = manager.getCenterFreq(band) ?: 0

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 24
                    bottomMargin = 24
                }
                setPadding(0, 8, 0, 8)
            }

            val freqLabel = TextView(requireContext(), null, 0, R.style.LabelSmall).apply {
                text = if (freq >= 1000) {
                    if (freq % 1000 == 0) {
                        "${freq / 1000} kHz"
                    } else {
                        String.format("%.1f kHz", freq / 1000f)
                    }
                } else {
                    "$freq Hz"
                }
                width = 120
            }
            row.addView(freqLabel)

            val initialLevel = savedLevels.getOrNull(i) ?: (manager.getBandLevel(band)?.toInt() ?: 0)
            val dbLabel = TextView(requireContext(), null, 0, R.style.LabelSmall).apply {
                text = "${(initialLevel.toInt() / 100)} dB"
                setPadding(12, 0, 0, 0)
                width = 120
                gravity = Gravity.END
            }

            val seekBar = SeekBar(requireContext()).apply {
                max = maxLevel - minLevel
                progress = initialLevel.toInt() - minLevel
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val thisLevel = (progress + minLevel).toShort()
                        if (fromUser) {
                            manager.setBandLevel(band, thisLevel)
                            saveBandLevelsToPreferences()
                        }
                        dbLabel.text = "${((progress + minLevel) / 100)} dB"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }
            bandSeekBars.add(seekBar)
            row.addView(seekBar)
            row.addView(dbLabel)
            eqBandsContainer.addView(row)
        }
    }

    private fun resetEqualizer() {
        val manager = equalizerManager ?: return
        val bands = manager.getNumberOfBands()
        val bandLevelRange = manager.getBandLevelRange() ?: shortArrayOf(-1500, 1500)
        val minLevel = bandLevelRange[0].toInt()
        val midLevel = 0
        for (i in 0 until bands) {
            manager.setBandLevel(i.toShort(), midLevel.toShort())
            bandSeekBars.getOrNull(i)?.progress = midLevel - minLevel
        }
        Preferences.setEqualizerBandLevels(ShortArray(bands.toInt()))
    }

    private fun saveBandLevelsToPreferences() {
        val manager = equalizerManager ?: return
        val bands = manager.getNumberOfBands()
        val levels = ShortArray(bands.toInt()) { i -> manager.getBandLevel(i.toShort()) ?: 0 }
        Preferences.setEqualizerBandLevels(levels)
    }

    private fun restoreEqualizerPreferences() {
        val manager = equalizerManager ?: return
        eqSwitch.isChecked = Preferences.isEqualizerEnabled()
        updateUiEnabledState(eqSwitch.isChecked)
        val bands = manager.getNumberOfBands()
        val bandLevelRange = manager.getBandLevelRange() ?: shortArrayOf(-1500, 1500)
        val minLevel = bandLevelRange[0].toInt()
        val savedLevels = Preferences.getEqualizerBandLevels(bands)
        if (savedLevels != null) {
            for (i in 0 until bands) {
                manager.setBandLevel(i.toShort(), savedLevels[i])
                bandSeekBars.getOrNull(i)?.progress = savedLevels[i] - minLevel
            }
        }
    }
}