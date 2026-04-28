package com.pumpcurve

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var checkBoxContainer: LinearLayout
    private lateinit var lineChart: LineChart
    private lateinit var editPressureMin: EditText
    private lateinit var editPressureMax: EditText
    private lateinit var checkLogX: CheckBox
    private lateinit var btnPlot: Button
    private lateinit var btnClear: Button
    private lateinit var btnExport: Button
    private lateinit var textDataInfo: TextView

    private val pumpDataMap = mutableMapOf<String, MutableList<Pair<Float, Float>>>()
    private val pumpCheckBoxes = mutableMapOf<String, android.widget.CheckBox>()
    private val lineColors = mutableListOf(
        Color.parseColor("#E53935"), Color.parseColor("#1E88E5"),
        Color.parseColor("#43A047"), Color.parseColor("#FB8C00"),
        Color.parseColor("#8E24AA"), Color.parseColor("#00ACC1"),
        Color.parseColor("#F4511E"), Color.parseColor("#3949AB"),
        Color.parseColor("#7CB342"), Color.parseColor("#D81B60"),
        Color.parseColor("#039BE5"), Color.parseColor("#00897B"),
        Color.parseColor("#FFB300"), Color.parseColor("#6D4C41")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkBoxContainer = findViewById(R.id.checkBoxContainer)
        lineChart = findViewById(R.id.lineChart)
        editPressureMin = findViewById(R.id.editPressureMin)
        editPressureMax = findViewById(R.id.editPressureMax)
        checkLogX = findViewById(R.id.checkLogX)
        btnPlot = findViewById(R.id.btnPlot)
        btnClear = findViewById(R.id.btnClear)
        btnExport = findViewById(R.id.btnExport)
        textDataInfo = findViewById(R.id.textDataInfo)

        setupChart()
        loadCsvData()

        btnPlot.setOnClickListener { plotCurves() }
        btnClear.setOnClickListener { clearChart() }
        btnExport.setOnClickListener { exportChart() }
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = true
            description.text = "Pressure (Torr) vs Pumping Speed (L/min)"
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = true
            legend.textSize = 9f
            axisRight.isEnabled = true
            axisLeft.textSize = 9f
            xAxis.textSize = 9f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
        }
    }

    private fun loadCsvData() {
        try {
            val reader = BufferedReader(InputStreamReader(assets.open("DATABASE.csv")))
            reader.readLine() // skip header
            reader.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 3) {
                    val pump = parts[0].trim()
                    val pressure = parts[1].trim().toFloatOrNull() ?: return@forEachLine
                    val speed = parts[2].trim().toFloatOrNull() ?: return@forEachLine
                    if (pump.isEmpty()) return@forEachLine
                    pumpDataMap.getOrPut(pump) { mutableListOf() }.add(Pair(pressure, speed))
                }
            }
            reader.close()

            val totalPoints = pumpDataMap.values.sumOf { it.size }
            val pumpList = pumpDataMap.keys.sorted()
            textDataInfo.text = "泵型号: ${pumpList.size} 个 | 数据点: $totalPoints 个"

            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 2

            pumpList.forEachIndexed { index, pump ->
                val cb = CheckBox(this).apply {
                    text = pump
                    textSize = 11f
                    layoutParams = lp
                    setOnCheckedChangeListener { _, _ -> plotCurves() }
                }
                pumpCheckBoxes[pump] = cb
                checkBoxContainer.addView(cb)
            }
        } catch (e: Exception) {
            textDataInfo.text = "加载CSV失败: ${e.message}"
        }
    }

    private fun plotCurves() {
        val selectedPumps = pumpCheckBoxes.filter { it.value.isChecked }.keys.toList()
        if (selectedPumps.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个泵型号", Toast.LENGTH_SHORT).show()
            return
        }

        val minP = editPressureMin.text.toString().toFloatOrNull()
        val maxP = editPressureMax.text.toString().toFloatOrNull()
        val useLogX = checkLogX.isChecked

        val dataSets = selectedPumps.mapIndexed { idx, pump ->
            val entries = pumpDataMap[pump]!!.filter { (p, _) ->
                (minP == null || p >= minP) && (maxP == null || p <= maxP)
            }.map { (p, s) -> Entry(p, s) }

            LineDataSet(entries, pump).apply {
                color = lineColors[idx % lineColors.size]
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
        }

        lineChart.data = LineData(dataSets.toList())

        if (useLogX) {
            lineChart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value < 1) String.format("%.2f", value)
                    else if (value < 10) String.format("%.1f", value)
                    else value.toInt().toString()
                }
            }
            lineChart.xAxis.axisMinimum = 1e-4f
            lineChart.xAxis.isLogarithmic = true
        } else {
            lineChart.xAxis.isLogarithmic = false
        }

        lineChart.invalidate()
    }

    private fun clearChart() {
        lineChart.clear()
        pumpCheckBoxes.values.forEach { it.isChecked = false }
        textDataInfo.text = "已清空"
    }

    private fun exportChart() {
        try {
            val fileName = "PumpCurve_${System.currentTimeMillis()}.png"
            val file = java.io.File(getExternalFilesDir(null), fileName)
            val bitmap = lineChart.chartBitmap
            java.io.FileOutputStream(file).use { fos ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            }
            Toast.makeText(this, "已保存: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
