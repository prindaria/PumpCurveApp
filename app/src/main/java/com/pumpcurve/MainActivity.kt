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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var pumpSelectionContainer: LinearLayout
    private lateinit var etPressureMin: EditText
    private lateinit var etPressureMax: EditText
    private lateinit var etSpeedMin: EditText
    private lateinit var etSpeedMax: EditText
    private lateinit var spinnerXUnit: Spinner
    private lateinit var spinnerYUnit: Spinner
    private lateinit var cbLogX: CheckBox
    private lateinit var cbLogY: CheckBox
    private lateinit var btnPlot: Button
    private lateinit var btnClear: Button
    private lateinit var btnExport: Button
    private lateinit var tvDataInfo: TextView
    private lateinit var etSearch: EditText
    private lateinit var tvTouchInfo: TextView  // 触摸坐标显示
    private lateinit var btnConfirm: Button    // 确认按钮

    // 压力单位
    // 1 Torr = 133.322 Pa = 1.33322 mbar
    // toTorr: 1个该单位 = toTorr Torr
    enum class PressureUnit(val displayName: String, val toTorr: Double) {
        TORR("Torr", 1.0),              // 1 Torr = 1 Torr
        PA("Pa", 1.0 / 133.322),        // 1 Pa = 1/133.322 Torr (1 Torr = 133.322 Pa)
        MBAR("mbar", 1.0 / 1.33322)     // 1 mbar = 1/1.33322 Torr (1 Torr = 1.33322 mbar)
    }

    // 速度单位
    // 6 m³/h = 100 L/min → 1 m³/h = 100/6 ≈ 16.67 L/min
    // toLmin: 1个该单位 = toLmin L/min
    enum class SpeedUnit(val displayName: String, val toLmin: Double) {
        LMIN("L/min", 1.0),             // 1 L/min = 1 L/min
        M3H("m³/h", 100.0 / 6.0)        // 6 m³/h = 100 L/min → 1 m³/h = 100/6 L/min
    }

    // 数据结构：Map<泵型号, List<数据点>>
    private var pumpData: Map<String, List<PumpDataPoint>> = emptyMap()
    private val pumpCheckBoxes = mutableMapOf<String, CheckBox>()
    private var allPumpModels: List<String> = emptyList()
    
    // 保存勾选状态（用于搜索后恢复勾选）
    private val checkedModels = mutableSetOf<String>()
    private var currentKeyword = ""

    // 当前选择的单位
    private var currentXUnit = PressureUnit.TORR
    private var currentYUnit = SpeedUnit.LMIN

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
        val pressure: Double,    // Torr (原始数据)
        val pumpingSpeed: Double  // L/min (原始数据)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupSpinners()
        loadDataFromCSV()
        setupChart()
        setupListeners()
        updateDataInfo()
        displayPumpModels("") // 初始显示所有型号
        
        // 默认勾选对数坐标
        cbLogX.isChecked = true
        cbLogY.isChecked = true
    }

    private fun initViews() {
        chart = findViewById(R.id.chart)
        pumpSelectionContainer = findViewById(R.id.pumpSelectionContainer)
        etPressureMin = findViewById(R.id.etPressureMin)
        etPressureMax = findViewById(R.id.etPressureMax)
        etSpeedMin = findViewById(R.id.etSpeedMin)
        etSpeedMax = findViewById(R.id.etSpeedMax)
        spinnerXUnit = findViewById(R.id.spinnerXUnit)
        spinnerYUnit = findViewById(R.id.spinnerYUnit)
        cbLogX = findViewById(R.id.cbLogX)
        cbLogY = findViewById(R.id.cbLogY)
        btnPlot = findViewById(R.id.btnPlot)
        btnClear = findViewById(R.id.btnClear)
        btnExport = findViewById(R.id.btnExport)
        tvDataInfo = findViewById(R.id.tvDataInfo)
        etSearch = findViewById(R.id.etSearch)
        tvTouchInfo = findViewById(R.id.tvTouchInfo)
        btnConfirm = findViewById(R.id.btnConfirm)
    }

    private fun setupSpinners() {
        // X轴单位
        val xUnits = PressureUnit.values().map { it.displayName }
        spinnerXUnit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, xUnits)
        spinnerXUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentXUnit = PressureUnit.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Y轴单位
        val yUnits = SpeedUnit.values().map { it.displayName }
        spinnerYUnit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yUnits)
        spinnerYUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentYUnit = SpeedUnit.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
            allPumpModels = pumpData.keys.sorted()

        } catch (e: Exception) {
            Toast.makeText(this, "加载CSV数据失败: ${e.message}", Toast.LENGTH_LONG).show()
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
        
        // 设置触摸监听，显示坐标
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e != null) {
                    val xValue = e.x
                    val yValue = e.y
                    val useLogX = cbLogX.isChecked
                    val useLogY = cbLogY.isChecked
                    
                    // 如果是对数坐标，转换回实际值
                    val actualX = if (useLogX) 10.0.pow(xValue.toDouble()) else xValue.toDouble()
                    val actualY = if (useLogY) 10.0.pow(yValue.toDouble()) else yValue.toDouble()
                    
                    // 转换单位显示
                    val displayX = actualX * currentXUnit.toTorr
                    val displayY = actualY * currentYUnit.toLmin
                    
                    val xUnit = currentXUnit.displayName
                    val yUnit = currentYUnit.displayName
                    
                    tvTouchInfo.text = "压力: ${formatNumber(displayX)} $xUnit  |  抽速: ${formatNumber(displayY)} $yUnit"
                    tvTouchInfo.visibility = TextView.VISIBLE
                }
            }

            override fun onNothingSelected() {
                tvTouchInfo.visibility = TextView.GONE
            }
        })
    }

    private fun setupListeners() {
        btnPlot.setOnClickListener { plotCurves() }
        btnClear.setOnClickListener { clearChart() }
        btnExport.setOnClickListener { exportChart() }
        
        // 确认按钮：实时刷新曲线
        btnConfirm.setOnClickListener { 
            if (checkedModels.isEmpty()) {
                Toast.makeText(this, "请至少选择一个泵型号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            plotCurves() 
        }

        cbLogX.setOnCheckedChangeListener { _, _ -> updateAxisScale() }
        cbLogY.setOnCheckedChangeListener { _, _ -> updateAxisScale() }

        // 搜索框文本变化监听
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s?.toString() ?: ""
                if (keyword != currentKeyword) {
                    // 保存当前勾选状态
                    saveCurrentCheckedState()
                    currentKeyword = keyword
                    displayPumpModels(keyword)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    
    private fun saveCurrentCheckedState() {
        // 保存当前显示的勾选状态
        pumpCheckBoxes.forEach { (model, checkBox) ->
            if (checkBox.isChecked) {
                checkedModels.add(model)
            } else {
                checkedModels.remove(model)
            }
        }
    }
    
    private fun restoreCheckedState() {
        // 恢复勾选状态
        pumpCheckBoxes.forEach { (model, checkBox) ->
            checkBox.isChecked = checkedModels.contains(model)
        }
    }

    private fun updateDataInfo() {
        val totalPoints = pumpData.values.sumOf { it.size }
        val pumpCount = pumpData.size
        tvDataInfo.text = "数据信息: $pumpCount 种泵型号, 共$totalPoints 个数据点"
    }

    private fun displayPumpModels(keyword: String) {
        pumpSelectionContainer.removeAllViews()
        pumpCheckBoxes.clear()

        val filtered = if (keyword.isEmpty()) {
            allPumpModels
        } else {
            allPumpModels.filter { it.contains(keyword, ignoreCase = true) }
        }

        for (model in filtered) {
            val checkBox = CheckBox(this)
            checkBox.text = model
            checkBox.textSize = 11f
            checkBox.setLines(1)
            pumpCheckBoxes[model] = checkBox
            pumpSelectionContainer.addView(checkBox)
        }
        
        // 恢复勾选状态
        restoreCheckedState()

        if (filtered.isEmpty()) {
            val infoText = TextView(this)
            infoText.text = "未找到匹配的型号"
            infoText.textSize = 10f
            infoText.setTextColor(Color.GRAY)
            pumpSelectionContainer.addView(infoText)
        }
    }

    private fun plotCurves() {
        // 保存勾选状态
        saveCurrentCheckedState()
        
        val selectedModels = checkedModels.toList()
        if (selectedModels.isEmpty()) {
            Toast.makeText(this, "请至少选择一个泵型号", Toast.LENGTH_SHORT).show()
            return
        }

        chart.clear()
        val dataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()

        var colorIndex = 0
        var hasData = false

        // 读取范围设置（转换为 Torr/L/min 进行过滤）
        val pressureMinTorr = parsePressure(etPressureMin.text.toString(), currentXUnit)
        val pressureMaxTorr = parsePressure(etPressureMax.text.toString(), currentXUnit)
        val speedMinLmin = parseSpeed(etSpeedMin.text.toString(), currentYUnit)
        val speedMaxLmin = parseSpeed(etSpeedMax.text.toString(), currentYUnit)

        val useLogX = cbLogX.isChecked
        val useLogY = cbLogY.isChecked

        for (model in selectedModels) {
            val points = pumpData[model] ?: continue

            // 按 X 轴排序
            val sortedPoints = points.sortedBy { it.pressure }

            val entries = mutableListOf<Entry>()
            for (point in sortedPoints) {
                // 原始数据（Torr, L/min）
                val xTorr = point.pressure  // Torr
                val yLmin = point.pumpingSpeed // L/min

                // 过滤范围（用 Torr/L/min 比较）
                if (pressureMinTorr != null && xTorr < pressureMinTorr) continue
                if (pressureMaxTorr != null && xTorr > pressureMaxTorr) continue
                if (speedMinLmin != null && yLmin < speedMinLmin) continue
                if (speedMaxLmin != null && yLmin > speedMaxLmin) continue

                // 转换为显示单位
                // toTorr 表示：1个该单位 = toTorr Torr
                // 所以显示值 = Torr / toTorr
                val xDisplay = (xTorr / currentXUnit.toTorr).toFloat()
                val yDisplay = (yLmin / currentYUnit.toLmin).toFloat()

                // 对数坐标转换
                var finalX = xDisplay
                var finalY = yDisplay
                if (useLogX && finalX > 0) {
                    finalX = log10(finalX.toDouble()).toFloat()
                }
                if (useLogY && finalY > 0) {
                    finalY = log10(finalY.toDouble()).toFloat()
                }

                entries.add(Entry(finalX, finalY))
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
            Toast.makeText(this, "指定范围内无数据", Toast.LENGTH_SHORT).show()
            return
        }

        val lineData = LineData(dataSets)
        chart.data = lineData

        // 设置坐标轴标签
        updateAxisLabels()

        chart.invalidate()
        Toast.makeText(this, "已绘制${selectedModels.size}条曲线", Toast.LENGTH_SHORT).show()
    }

    private fun parsePressure(text: String, unit: PressureUnit): Float? {
        val value = text.toFloatOrNull() ?: return null
        // 转换为 Torr：显示值 * toTorr = Torr
        return (value * unit.toTorr.toFloat())
    }

    private fun parseSpeed(text: String, unit: SpeedUnit): Float? {
        val value = text.toFloatOrNull() ?: return null
        // 转换为 L/min：显示值 * toLmin = L/min
        return (value * unit.toLmin.toFloat())
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

        // X轴标签：对数坐标显示实际值
        if (useLogX) {
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val original = 10.0.pow(value.toDouble())
                    // 转换回显示单位
                    val displayValue = original * currentXUnit.toTorr
                    return formatNumber(displayValue)
                }
            }
        } else {
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    // 转换回显示单位
                    val displayValue = value.toDouble() * currentXUnit.toTorr
                    return formatNumber(displayValue)
                }
            }
        }

        // Y轴标签：对数坐标显示实际值
        if (useLogY) {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val original = 10.0.pow(value.toDouble())
                    // 转换回显示单位
                    val displayValue = original * currentYUnit.toLmin
                    return formatNumber(displayValue)
                }
            }
        } else {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    // 转换回显示单位
                    val displayValue = value.toDouble() * currentYUnit.toLmin
                    return formatNumber(displayValue)
                }
            }
        }

        // 更新轴单位标签
        chart.xAxis.labelRotationAngle = 0f
    }

    // 正常计数法格式化（不用科学计数法）
    private fun formatNumber(value: Double): String {
        return when {
            value >= 1_000_000 -> "%.0f".format(value)
            value >= 1_000 -> "%.0f".format(value)
            value >= 1 -> "%.1f".format(value)
            value >= 0.001 -> "%.4f".format(value)
            else -> "%.6f".format(value)
        }
    }

    private fun clearChart() {
        chart.clear()
        // 清除所有勾选状态
        checkedModels.clear()
        pumpCheckBoxes.forEach { it.value.isChecked = false }
        etPressureMin.text.clear()
        etPressureMax.text.clear()
        etSpeedMin.text.clear()
        etSpeedMax.text.clear()
        etSearch.text.clear()
        tvTouchInfo.visibility = TextView.GONE
        Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
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
