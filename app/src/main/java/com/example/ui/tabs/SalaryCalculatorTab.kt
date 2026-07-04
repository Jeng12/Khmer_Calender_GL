package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.GREG_MONTHS_EN
import com.example.core.LocalAppLanguage
import com.example.core.tr
import com.example.data.AppStore
import com.example.data.Holiday
import com.example.data.HolidayRepository
import com.example.data.WorkCycleEngine
import com.example.ui.theme.CrimsonHoliday
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TraditionalGold
import java.util.Calendar
import kotlin.math.max

private const val STANDARD_DAILY_HOURS = 8.0
private const val STANDARD_WEEKLY_HOURS = 48.0
private const val HOUR_MS = 3_600_000.0

@Composable
fun SalaryCalculatorTabContent() {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val today = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    val monthKey = "%04d-%02d".format(selectedYear, selectedMonth)

    var settings by remember(monthKey) { mutableStateOf(AppStore.getSalaryCalculatorSettings(context, monthKey)) }
    var holidaysResult by remember(selectedYear) { mutableStateOf<Result<List<Holiday>>?>(null) }

    LaunchedEffect(monthKey, settings) {
        AppStore.saveSalaryCalculatorSettings(context, settings, monthKey)
    }

    LaunchedEffect(selectedYear) {
        holidaysResult = null
        holidaysResult = HolidayRepository.fetchHolidays(context = context, year = selectedYear, includeDatabaseEvents = AppStore.isCloudSyncEnabled(context))
    }

    fun changeMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            clear()
            set(selectedYear, selectedMonth - 1, 1)
            add(Calendar.MONTH, delta)
        }
        selectedYear = cal.get(Calendar.YEAR)
        selectedMonth = cal.get(Calendar.MONTH) + 1
    }

    val summary = remember(selectedYear, selectedMonth, settings, holidaysResult) {
        buildSalarySummary(context, selectedYear, selectedMonth, settings, holidaysResult?.getOrNull().orEmpty())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(nightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(tr("គណនាប្រាក់ខែ", "Salary Calculator"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = sandText)
                Text(tr("រក្សាទុកតាមខែ នៅក្នុងទូរស័ព្ទប៉ុណ្ណោះ", "Saved by month on this device only"), fontSize = 10.sp, color = goldSubText)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(plumSurface, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, deepBorder), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("<", fontSize = 22.sp, color = TraditionalGold, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { changeMonth(-1) }.padding(horizontal = 16.dp, vertical = 4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${GREG_MONTHS_EN[selectedMonth - 1]} $selectedYear", fontSize = 15.sp, color = sandText, fontWeight = FontWeight.Bold)
                    Text(tr("ខែដែលត្រូវគណនា", "Selected salary month"), fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Text(">", fontSize = 22.sp, color = TraditionalGold, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { changeMonth(1) }.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(plumSurface, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, deepBorder), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Hourly rate", settings.hourlyRate, { settings = settings.copy(hourlyRate = it) }, Modifier.weight(1f))
                    SalaryInput("Daily rate", settings.dailyRate, { settings = settings.copy(dailyRate = it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Overtime x", settings.overtimeRate, { settings = settings.copy(overtimeRate = it) }, Modifier.weight(1f))
                    SalaryInput("Night shift x", settings.nightShiftRate, { settings = settings.copy(nightShiftRate = it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Holiday day x", settings.holidayDayRate, { settings = settings.copy(holidayDayRate = it) }, Modifier.weight(1f))
                    SalaryInput("Holiday night x", settings.holidayNightRate, { settings = settings.copy(holidayNightRate = it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Tax / VAT %", settings.taxVatPercent, { settings = settings.copy(taxVatPercent = it) }, Modifier.weight(1f))
                    SalaryInput("Benefits", settings.benefits, { settings = settings.copy(benefits = it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Bonus override", settings.bonuses, { settings = settings.copy(bonuses = it) }, Modifier.weight(1f))
                    SalaryInput("Allowances", settings.allowances, { settings = settings.copy(allowances = it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInput("Other deductions", settings.otherDeductions, { settings = settings.copy(otherDeductions = it) }, Modifier.weight(1f))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(plumCard, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, TraditionalGold.copy(0.25f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (holidaysResult == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TraditionalGold)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("កំពុងទាញថ្ងៃបុណ្យ...", "Loading holidays..."), fontSize = 10.sp, color = goldSubText)
                    }
                } else if (holidaysResult?.isFailure == true) {
                    Text(tr("មិនអាចទាញថ្ងៃបុណ្យ API បាន។ ការគណនាថ្ងៃបុណ្យអាចមិនពេញលេញ។", "Holiday API failed. Holiday pay may be incomplete."), fontSize = 10.sp, color = CrimsonHoliday)
                }
                SalarySummaryRow("Working days", summary.workingDays.toString())
                SalarySummaryRow("Total work hours", summary.totalHours.hourLabel(), highlight = true)
                SalarySummaryRow("Incl. Overtime", summary.overtimeHours.hourLabel())
                HorizontalDivider(color = deepBorder, modifier = Modifier.padding(vertical = 4.dp))
                SalarySummaryRow("Day hours", summary.dayHours.hourLabel())
                SalarySummaryRow("Night shift hours", summary.nightHours.hourLabel())
                SalarySummaryRow("Holiday day hours", summary.holidayDayHours.hourLabel())
                SalarySummaryRow("Holiday night hours", summary.holidayNightHours.hourLabel())
                HorizontalDivider(color = deepBorder)
                SalarySummaryRow("Base pay (Total × Rate)", summary.basePay.moneyLabel())
                SalarySummaryRow("Overtime premium", summary.overtimePremium.moneyLabel())
                SalarySummaryRow("Night shift premium", summary.nightPremium.moneyLabel())
                SalarySummaryRow("Holiday day premium", summary.holidayDayPremium.moneyLabel())
                SalarySummaryRow("Holiday night premium", summary.holidayNightPremium.moneyLabel())
                SalarySummaryRow("Default/override bonus", summary.bonusPay.moneyLabel())
                SalarySummaryRow("Benefits + allowances", summary.additions.moneyLabel())
                SalarySummaryRow("Gross salary", summary.grossSalary.moneyLabel(), highlight = true)
                SalarySummaryRow("Tax / VAT deduction", "-${summary.taxDeduction.moneyLabel()}")
                SalarySummaryRow("Other deductions", "-${summary.otherDeductions.moneyLabel()}")
                SalarySummaryRow("Net salary", summary.netSalary.moneyLabel(), highlight = true)
                if (summary.workingDays == 0) {
                    Text(tr("មិនមានវេនការងារសម្រាប់ខែនេះ", "No saved work shifts for this month."), fontSize = 10.sp, color = dimColor)
                }
            }
        }
    }
}

internal data class SalarySummary(
    val workingDays: Int,
    val totalHours: Double,
    val dayHours: Double,
    val nightHours: Double,
    val holidayDayHours: Double,
    val holidayNightHours: Double,
    val overtimeHours: Double,
    val basePay: Double,
    val overtimePremium: Double,
    val nightPremium: Double,
    val holidayDayPremium: Double,
    val holidayNightPremium: Double,
    val bonusPay: Double,
    val additions: Double,
    val grossSalary: Double,
    val taxDeduction: Double,
    val otherDeductions: Double,
    val netSalary: Double
)

private fun buildSalarySummary(
    context: android.content.Context,
    year: Int,
    month: Int,
    settings: AppStore.SalaryCalculatorSettings,
    holidays: List<Holiday>
): SalarySummary {
    val base = AppStore.getShiftCycle(context)
    val schedules = AppStore.getCycleSnapshots(context)
    val from = WorkCycleEngine.cycleStart(year, month, 26) // Start of 26-25 cycle
    val to = (from.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }

    // Holiday categorization: check all days in the cycle range
    val holidayDays = holidays.map { h ->
        h.date.year * 10000 + h.date.monthValue * 100 + h.date.dayOfMonth
    }.toSet()

    val workDays = if (base == null) {
        emptyList()
    } else {
        WorkCycleEngine.buildWorkDays(
            cycleFor = { y, m, d -> AppStore.cycleForDate(base, schedules, y, m, d) },
            fromCal = from,
            toCal = to
        ).filterNot { it.blocked }
    }
    return calculateSalarySummary(workDays, holidayDays, settings)
}

internal fun calculateSalarySummary(
    workDays: List<WorkCycleEngine.WorkDay>,
    holidayDays: Set<Int>, // Set of YYYYMMDD integers
    settings: AppStore.SalaryCalculatorSettings
): SalarySummary {
    val dayHours = workDays.filter { !it.shift.isOvernight && (it.year * 10000 + it.month * 100 + it.day) !in holidayDays }.sumOf { (it.endMs - it.startMs) / HOUR_MS }
    val nightHours = workDays.filter { it.shift.isOvernight && (it.year * 10000 + it.month * 100 + it.day) !in holidayDays }.sumOf { (it.endMs - it.startMs) / HOUR_MS }
    val holidayDayHours = workDays.filter { !it.shift.isOvernight && (it.year * 10000 + it.month * 100 + it.day) in holidayDays }.sumOf { (it.endMs - it.startMs) / HOUR_MS }
    val holidayNightHours = workDays.filter { it.shift.isOvernight && (it.year * 10000 + it.month * 100 + it.day) in holidayDays }.sumOf { (it.endMs - it.startMs) / HOUR_MS }

    // Total Work Hours = Day + Night + Holiday Day + Holiday Night
    val totalHours = dayHours + nightHours + holidayDayHours + holidayNightHours

    // Overtime = WeeklyOT per week (hours exceeding the regular weekly limit of 48 hours)
    val overtimeHours = workDays
        .groupBy { weekKey(it.year, it.month, it.day) }
        .values
        .sumOf { week ->
            max(0.0, week.sumOf { (it.endMs - it.startMs) / HOUR_MS } - STANDARD_WEEKLY_HOURS)
        }

    val hourlyRate = settings.hourlyRate.moneyValue()
    val otRate = settings.overtimeRate.moneyValue()
    val nRate = settings.nightShiftRate.moneyValue()
    val hdRate = settings.holidayDayRate.moneyValue()
    val hnRate = settings.holidayNightRate.moneyValue()

    // Base Pay = Total Work Hours × Hourly Rate
    val basePay = totalHours * hourlyRate

    // Premiums (Additional multiplier amount)
    val overtimePremium = overtimeHours * hourlyRate * max(0.0, otRate - 1.0)
    val nightPremium = nightHours * hourlyRate * max(0.0, nRate - 1.0)
    val holidayDayPremium = holidayDayHours * hourlyRate * max(0.0, hdRate - 1.0)
    val holidayNightPremium = holidayNightHours * hourlyRate * max(0.0, hnRate - 1.0)

    val defaultBonus = (settings.dailyRate.moneyValue() * (totalHours / 8.0)) / 6.0
    val bonusPay = settings.bonuses.trim().toDoubleOrNull() ?: defaultBonus
    val additions = settings.benefits.moneyValue() + settings.allowances.moneyValue()

    // Gross Pay = Base Pay + All Premiums + Bonuses + Additions
    val gross = basePay + overtimePremium + nightPremium + holidayDayPremium + holidayNightPremium + bonusPay + additions
    val tax = gross * (settings.taxVatPercent.moneyValue() / 100.0)
    val otherDeductions = settings.otherDeductions.moneyValue()

    return SalarySummary(
        workingDays = workDays.size,
        totalHours = totalHours,
        dayHours = dayHours,
        nightHours = nightHours,
        holidayDayHours = holidayDayHours,
        holidayNightHours = holidayNightHours,
        overtimeHours = overtimeHours,
        basePay = basePay,
        overtimePremium = overtimePremium,
        nightPremium = nightPremium,
        holidayDayPremium = holidayDayPremium,
        holidayNightPremium = holidayNightPremium,
        bonusPay = bonusPay,
        additions = additions,
        grossSalary = gross,
        taxDeduction = tax,
        otherDeductions = otherDeductions,
        netSalary = gross - tax - otherDeductions
    )
}

private fun weekKey(year: Int, month: Int, day: Int): String {
    val c = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    return "${c.get(Calendar.YEAR)}-${c.get(Calendar.WEEK_OF_YEAR)}"
}

@Composable
private fun SalaryInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val (_, _, plumSurface, _, deepBorder, _, sandText, goldSubText, _) = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' }.take(12)) },
        label = { Text(label, fontSize = 10.sp, color = goldSubText) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(color = sandText, fontSize = 12.sp),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TraditionalGold,
            unfocusedBorderColor = deepBorder,
            focusedContainerColor = plumSurface,
            unfocusedContainerColor = plumSurface
        )
    )
}

@Composable
private fun SalarySummaryRow(label: String, value: String, highlight: Boolean = false) {
    val (_, _, _, _, _, _, sandText, goldSubText, _) = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = if (highlight) 12.sp else 10.sp, color = if (highlight) sandText else goldSubText, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = if (highlight) 13.sp else 11.sp, color = if (highlight) TraditionalGold else sandText, fontWeight = FontWeight.Bold)
    }
}

private fun String.moneyValue(): Double = trim().toDoubleOrNull() ?: 0.0

private fun Double.moneyLabel(): String = "$" + "%,.2f".format(this)

private fun Double.hourLabel(): String = if (this % 1.0 == 0.0) "${toInt()} h" else "%.1f h".format(this)
