package com.rise.blescanner

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rise.blescanner.databinding.ActivitySettingBinding
import com.rise.blescanner.dialog.BleSelectorDialog
import com.rise.blescanner.store.BleDeviceConfig
import com.rise.blescanner.store.ConfigStore

@SuppressLint("SetTextI18n")
class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding
    private lateinit var configStore: ConfigStore

    private var tempBle1: BleDeviceConfig? = null
    private var tempBle2: BleDeviceConfig? = null
    private var tempBle3: BleDeviceConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        configStore = ConfigStore(this)

        loadCurrentConfigurations()
        setupClickListeners()
    }

    private fun loadCurrentConfigurations() {
        tempBle1 = configStore.getBle1()
        tempBle2 = configStore.getBle2()
        tempBle3 = configStore.getBle3()

        updateUiForAnchor(1, tempBle1)
        updateUiForAnchor(2, tempBle2)
        updateUiForAnchor(3, tempBle3)

        binding.switchRecordAccel.isChecked = configStore.getRecordAccel()
        binding.switchRecordGyro.isChecked = configStore.getRecordGyro()
        binding.switchShowVisualizer.isChecked = configStore.getShowVisualizer()
    }


    private fun updateUiForAnchor(anchorNumber: Int, config: BleDeviceConfig?) {
        val labelView = when (anchorNumber) {
            1 -> binding.txtSelectedBle1
            2 -> binding.txtSelectedBle2
            3 -> binding.txtSelectedBle3
            else -> return
        }

        if (config != null) {
            labelView.text = "${config.name}\n${config.macAddress}"
            labelView.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            labelView.text = "Not Configured"
            labelView.setTextColor("#F44336".toColorInt())
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectBle1.setOnClickListener {
            showDeviceSelectorDialog(1)
        }

        binding.btnSelectBle2.setOnClickListener {
            showDeviceSelectorDialog(2)
        }

        binding.btnSelectBle3.setOnClickListener {
            showDeviceSelectorDialog(3)
        }

        binding.switchRecordAccel.setOnCheckedChangeListener { _, isChecked ->
            configStore.saveRecordAccel(isChecked)
        }

        binding.switchRecordGyro.setOnCheckedChangeListener { _, isChecked ->
            configStore.saveRecordGyro(isChecked)
        }

        binding.switchShowVisualizer.setOnCheckedChangeListener { _, isChecked ->
            configStore.saveShowVisualizer(isChecked)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun showDeviceSelectorDialog(anchorNumber: Int) {
        val dialog = BleSelectorDialog(this, object : BleSelectorDialog.BleSelectorCallback {
            override fun onDeviceSelected(config: BleDeviceConfig) {
                when (anchorNumber) {
                    1 -> {
                        tempBle1 = config
                        updateUiForAnchor(1, tempBle1)
                    }

                    2 -> {
                        tempBle2 = config
                        updateUiForAnchor(2, tempBle2)
                    }

                    3 -> {
                        tempBle3 = config
                        updateUiForAnchor(3, tempBle3)
                    }
                }
                saveAllConfigurations()
                Toast.makeText(
                    this@SettingActivity,
                    "Anchor $anchorNumber selected",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onCancelled() {
                Toast.makeText(this@SettingActivity, "Selection cancelled", Toast.LENGTH_SHORT)
                    .show()
            }
        })
        dialog.show()
    }

    private fun saveAllConfigurations() {
        tempBle1?.let { configStore.saveBle1(it) }
        tempBle2?.let { configStore.saveBle2(it) }
        tempBle3?.let { configStore.saveBle3(it) }
    }
}