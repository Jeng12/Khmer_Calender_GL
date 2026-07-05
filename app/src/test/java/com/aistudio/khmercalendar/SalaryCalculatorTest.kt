package com.aistudio.khmercalendar

import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.WorkCycleEngine
import com.aistudio.khmercalendar.ui.tabs.calculateSalarySummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SalaryCalculatorTest {

    @Test
    fun verifyUserExample() {
        // Business Rules Example:
        // 3 day shifts × 12 hours
        // 3 night shifts × 12 hours
        // Total worked hours = 72 hours
        // Regular weekly hours = 48 hours
        // Overtime hours = 72 − 48 = 24 hours
        val dayShift = AppStore.ShiftDef("day", "Day", 7, 30, 19, 30) // 12h
        val nightShift = AppStore.ShiftDef("night", "Night", 19, 30, 7, 30) // 12h
        val workDays = listOf(
            workDay(2026, 7, 6, dayShift), // Mon
            workDay(2026, 7, 7, dayShift), // Tue
            workDay(2026, 7, 8, dayShift), // Wed
            workDay(2026, 7, 9, nightShift), // Thu
            workDay(2026, 7, 10, nightShift), // Fri
            workDay(2026, 7, 11, nightShift)  // Sat
        )
        val settings = AppStore.SalaryCalculatorSettings(
            hourlyRate = "10",
            dailyRate = "80",
            overtimeRate = "1.5",
            nightShiftRate = "1.0",
            taxVatPercent = "0"
        )

        val summary = calculateSalarySummary(workDays, holidayDays = emptySet(), settings)

        // Rule: Total Work Hours = Day + Night + Holiday Day + Holiday Night
        assertEquals(72.0, summary.totalHours, 0.001)
        assertEquals(36.0, summary.dayHours, 0.001)
        assertEquals(36.0, summary.nightHours, 0.001)
        
        // Rule: Overtime hours should be treated as a property, not added again
        assertEquals(24.0, summary.overtimeHours, 0.001)

        // Rule: Base Pay = Total Work Hours × Hourly Rate
        assertEquals(720.0, summary.basePay, 0.001)

        // Rule: Overtime Premium = Overtime Hours × Hourly Rate × (1.5 - 1.0)
        assertEquals(120.0, summary.overtimePremium, 0.001)

        // Rule: Gross Pay = Base + Premiums + Bonuses + Additions
        // defaultBonus = (80 * (72/8)) / 6 = 120
        assertEquals(720.0 + 120.0 + 120.0, summary.grossSalary, 0.001)
    }

    @Test
    fun overlappingPremiumsAndHolidays() {
        val nightShift = AppStore.ShiftDef("night", "Night", 20, 0, 4, 0) // 8h
        val workDays = listOf(
            workDay(2026, 7, 6, nightShift)
        )
        // July 6 is holiday
        val holidayDays = setOf(20260706) 
        
        val settings = AppStore.SalaryCalculatorSettings(
            hourlyRate = "10",
            nightShiftRate = "1.1",
            holidayNightRate = "2.0",
            overtimeRate = "1.5"
        )
        
        // Suppose this was a 10h shift (2h OT)
        val longNightShift = AppStore.ShiftDef("night_ot", "Night OT", 18, 0, 4, 0) // 10h
        val summary = calculateSalarySummary(listOf(workDay(2026, 7, 6, longNightShift)), holidayDays, settings)
        
        assertEquals(10.0, summary.totalHours, 0.001)
        assertEquals(10.0, summary.holidayNightHours, 0.001)
        assertEquals(0.0, summary.overtimeHours, 0.001)
        
        // Base Pay = 10 * 10 = 100
        // OT Premium = 0 * 10 * 0.5 = 0
        // Holiday Night Premium = 10 * 10 * (2.0 - 1.0) = 100
        // Gross = 100 + 0 + 100 = 200 (plus bonus)
        assertEquals(200.0, summary.grossSalary, 0.1) // Adjust for bonus if needed
    }

    private fun workDay(year: Int, month: Int, day: Int, shift: AppStore.ShiftDef): WorkCycleEngine.WorkDay {
        val start = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, shift.startHour, shift.startMin)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, shift.endHour, shift.endMin)
            if (shift.isOvernight) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return WorkCycleEngine.WorkDay(year, month, day, shift, start, end, blocked = false)
    }
}
