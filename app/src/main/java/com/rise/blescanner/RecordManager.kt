package com.rise.blescanner

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.rise.blescanner.adapters.RecordAdapter
import com.rise.blescanner.databinding.ActivityRecordManagerBinding
import com.rise.blescanner.store.RecordStore

class RecordManager : AppCompatActivity() {

    private lateinit var binding: ActivityRecordManagerBinding
    private lateinit var recordStore: RecordStore

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecordManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recordStore = RecordStore(this)

        setupUI()
        loadRecordings()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadRecordings() {
        val recordings = recordStore.getRecordings().reversed()
        if (recordings.isEmpty()) {
            binding.txtNoRecordings.visibility = View.VISIBLE
            binding.rvRecordings.visibility = View.GONE
        } else {
            binding.txtNoRecordings.visibility = View.GONE
            binding.rvRecordings.visibility = View.VISIBLE
            binding.rvRecordings.layoutManager = LinearLayoutManager(this)
            binding.rvRecordings.adapter = RecordAdapter(recordings)
        }
    }
}
