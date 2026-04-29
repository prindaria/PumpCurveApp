package com.pumpcurve

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10

class MainActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var pumpSelectionContainer: LinearLayout
    private lateinit var etPressureMin: EditText
    private lateinit var etPressureMax: EditText
    private lateinit var cbLogX: CheckBox
    private lateinit var cbLogY: CheckBox
    private lateinit var btnPlot: Button
    private lateinit var btnClear: Button
    private lateinit var btnExport: Button
    private lateinit var tvDataInfo: TextView

    // 数据结构：Map<泵型号, List<数据点>>
    private var pumpData: Map<String, List<PumpDataPoint>> = emptyMap()
    private val pumpCheckBoxes = mutableMapOf<String, CheckBox>()

    // 颜色列表，用于不同泵型号的曲线
    private val colors = listOf(
        Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN,
        Color.YELLOW, Color.parseColor("#FF6B35"), Color.parseColor("#8B4513"),
        Color.parseColor("#4B0082"), Color.parseColor("#FF1493"),
        Color.parseColor("#00CED1"), Color.parseColor("#32CD32"),
        Color.parseColor("#FF4500"), Color.parseColor("#9400D3"),
        Color.parseColor("#1E90FF"), Color.parseColor("#FFD700")
    )

    data class PumpDataPoint(
        val pumpModel: String,
        val pressure: Double,    // Torr
        val pumpingSpeed: Double  // L/min
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadDataFromCSV()
        setupChart()
        setupListeners()
        updateDataInfo()
    }

    private fun initViews() {
        chart = findViewById(R.id.chart)
        pumpSelectionContainer = findViewById(R.id.pumpSelectionContainer)
        etPressureMin = findViewById(R.id.etPressureMin)
        etPressureMax = findViewById(R.id.etPressureMax)
        cbLogX = findViewById(R.id.cbLogX)
        cbLogY = findViewById(R.id.cbLogY)
        btnPlot = findViewById(R.id.btnPlot)
        btnClear = findViewById(R.id.btnClear)
        btnExport = findViewById(R.id.btnExport)
        tvDataInfo = findViewById(R.id.tvDataInfo)
    }

    private fun loadDataFromCSV() {
        try {
            val inputStream = assets.open("DATABASE.csv")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val dataPoints = mutableListOf<PumpDataPoint>()

            var line: String? = reader.readLine() // 跳过标题行
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val parts = it.split(",").map { s -> s.trim().replace("\"", "") }
                    if (parts.size >= 3) {
                        try {
                            val pumpModel = parts[0]
                            val pressure = parts[1].toDoubleOrNull() ?: return@let
                            val pumpingSpeed = parts[2].toDoubleOrNull() ?: return@let
                            dataPoints.add(PumpDataPoint(pumpModel, pressure, pumpingSpeed))
                        } catch (e: Exception) {
                            // 跳过解析失败的行
                        }
                    }
                }
            }
            reader.close()

            // 按泵型号分组
            pumpData = dataPoints.groupBy { it.pumpModel }
            createPumpCheckBoxes()

        } catch (e: Exception) {
            Toast.makeText(this, "加载CSV数据失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createPumpCheckBoxes() {
        pumpSelectionContainer.removeAllViews()
        pumpCheckBoxes.clear()

        // 排序泵型号
        val sortedModels = pumpData.keys.sorted()

        // 限制显示数量（太多会卡顿）
        val maxDisplay = 30
        val displayModels = if (sortedModels.size > maxDisplay) {
            sortedModels.subList(0, maxDisplay)
        } else {
            sortedModels
        }

        for (model in displayModels) {
            val checkBox = CheckBox(this)
            checkBox.text = model
            checkBox.textSize = 11f
            checkBox.setLines(1)
            pumpCheckBoxes[model] = checkBox
            pumpSelectionContainer.addView(checkBox)
        }

        if (sortedModels.size > maxDisplay) {
            val infoText = TextView(this)
            infoText.text = "(仅显示前${maxDisplay}个型号，共${sortedModels.size}个)"
            infoText.textSize = 10f
            infoText.setTextColor(Color.GRAY)
            pumpSelectionContainer.addView(infoText)
        }
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.legend.isWordWrapEnabled = true
        chart.legend.textSize = 10f
        chart.legend.formSize = 8f

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(true)
        chart.axisLeft.setDrawGridLines(true)
        chart.axisRight.isEnabled = false

        chart.xAxis.granularity = 1f
    }

    private fun setupListeners() {
        btnPlot.setOnClickListener { plotCurves() }
        btnClear.setOnClickListener { clearChart() }
        btnExport.setOnClickListener { exportChart() }

        cbLogX.setOnCheckedChangeListener { _, _ -> updateAxisScale() }
        cbLogY.setOnCheckedChangeListener { _, _ -> updateAxisScale() }
    }

    private fun updateDataInfo() {
        val totalPoints = pumpData.values.sumOf { it.size }
        val pumpCount = pumpData.size
        tvDataInfo.text = "数据信息: $pumpCount 种泵型号, 共 $totalPoints 个数据点"
    }

    private fun plotCurves() {
        val selectedModels = pumpCheckBoxes.filter { it.value.isChecked }.keys.toList()
        if (selectedModels.isEmpty()) {
            Toast.makeText(this, "请至少选择一个泵型号", Toast.LENGTH_SHORT).show()
            return
        }

        chart.clear()
        val dataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()

        var colorIndex = 0
        var hasData = false

        val pressureMinText = etPressureMin.text.toString()
        val pressureMaxText = etPressureMax.text.toString()
        val pressureMin = pressureMinText.toDoubleOrNull() ?: 0.0
        val pressureMax = pressureMaxText.toDoubleOrNull() ?: Double.MAX_VALUE

        val useLogX = cbLogX.isChecked
        val useLogY = cbLogY.isChecked

        for (model in selectedModels) {
            val points = pumpData[model] ?: continue

            // 按 X 轴排序
            val sortedPoints = points.sortedBy { it.pressure }

            val entries = mutableListOf<Entry>()
            for (point in sortedPoints) {
                var x = point.pressure.toFloat()
                var y = point.pumpingSpeed.toFloat()

                // 过滤范围
                if (x < pressureMin || x > pressureMax) continue

                // 对数坐标转换
                if (useLogX && x > 0) {
                    x = log10(x.toDouble()).toFloat()
                }
                if (useLogY && y > 0) {
                    y = log10(y.toDouble()).toFloat()
                }

                entries.add(Entry(x, y))
            }

            if (entries.isNotEmpty()) {
                hasData = true
                val color = colors[colorIndex % colors.size]
                val dataSet = LineDataSet(entries, model)
                dataSet.color = color
                dataSet.lineWidth = 1.5f
                dataSet.setDrawCircles(false)
                dataSet.setDrawValues(false)
                dataSet.setDrawIcons(false)
                dataSets.add(dataSet)
                colorIndex++
            }
        }

        if (!hasData) {
            Toast.makeText(this, "选定范围内无数据", Toast.LENGTH_SHORT).show()
            return
        }

        val lineData = LineData(dataSets)
        chart.data = lineData

        // 设置轴标签
        updateAxisLabels()

        chart.invalidate()
        Toast.makeText(this, "已绘制 ${selectedModels.size} 条曲线", Toast.LENGTH_SHORT).show()
    }

    private fun updateAxisScale() {
        // 当对数坐标切换时，重新绘制
        if (chart.data != null && chart.data!!.dataSetCount > 0) {
            plotCurves()
        }
    }

    private fun updateAxisLabels() {
        val useLogX = cbLogX.isChecked
        val useLogY = cbLogY.isChecked

        if (useLogX) {
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return "10^${value.toInt()}"
                }
            }
        } else {
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return "%.1f".format(value)
                }
            }
        }

        if (useLogY) {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return "10^${value.toInt()}"
                }
            }
        } else {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return "%.0f".format(value)
                }
            }
        }
    }

    private fun clearChart() {
        chart.clear()
        // 清除所有选中状态
        pumpCheckBoxes.forEach { it.value.isChecked = false }
        etPressureMin.text.clear()
        etPressureMax.text.clear()
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
    }

    private fun exportChart() {
        if (chart.data == null || chart.data!!.dataSetCount == 0) {
            Toast.makeText(this, "请先绘制曲线", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            return
        }

        try {
            val bitmap = chart.chartBitmap
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "PumpCurve_$timeStamp.png"

            // 保存到 Pictures 目录
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PumpCurveApp"
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val file = File(picturesDir, fileName)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()

            // 通知媒体库更新
            val mediaScanIntent = android.content.Intent(
                android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
            )
            val contentUri = android.net.Uri.fromFile(file)
            mediaScanIntent.data = contentUri
            sendBroadcast(mediaScanIntent)

            Toast.makeText(this, "已保存到: Pictures/PumpCurveApp/$fileName", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportChart()
            } else {
                Toast.makeText(this, "需要存储权限才能导出图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
    }
}