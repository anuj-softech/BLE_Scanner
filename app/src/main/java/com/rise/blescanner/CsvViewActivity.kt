package com.rise.blescanner

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rise.blescanner.databinding.ActivityCsvViewBinding
import java.io.File

class CsvViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCsvViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCsvViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        val csvPath = intent.getStringExtra("csv_path")
        if (csvPath.isNullOrEmpty()) {
            Toast.makeText(this, "CSV file path is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(csvPath)
        if (!file.exists()) {
            Toast.makeText(this, "CSV file does not exist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.txtTitle.text = file.name

        try {
            val lines = file.readLines()
            if (lines.isNotEmpty()) {
                val headerRow = createRow(lines[0].split(","), isHeader = true)
                binding.tableLayout.addView(headerRow)

                var renderedCount = 0
                var hasMoreRows = false
                for (i in 1 until lines.size) {
                    if (lines[i].isBlank()) continue
                    if (renderedCount >= 100) {
                        hasMoreRows = true
                        break
                    }
                    val dataRow = createRow(lines[i].split(","), isHeader = false)
                    binding.tableLayout.addView(dataRow)
                    renderedCount++
                }
                if (hasMoreRows) {
                    Toast.makeText(this, "File is large; displaying first 100 rows only.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createRow(columns: List<String>, isHeader: Boolean): TableRow {
        val tableRow = TableRow(this)
        val paddingDp = 8
        val scale = resources.displayMetrics.density
        val paddingPx = (paddingDp * scale + 0.5f).toInt()

        for (columnText in columns) {
            val textView = TextView(this).apply {
                text = columnText
                paddingPx.let { setPadding(it, it, it, it) }
                gravity = Gravity.CENTER
                textSize = 14f
                minWidth = (100 * scale + 0.5f).toInt()
                if (isHeader) {
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#4CAF50"))
                } else {
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.parseColor("#F5FBF4"))
                }
            }
            tableRow.addView(textView)
        }
        return tableRow
    }
}
