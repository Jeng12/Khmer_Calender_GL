# Khmer Calendar Algorithm Documentation

## Overview

`KhmerCalendarHelper.kt` implements a **lunisolar calendar engine** for the traditional Khmer (Cambodian) calendar. The calendar is based on the Theravada Buddhist calendar system, which closely follows the ancient Indian Surya Siddhanta astronomical tradition. The implementation combines:

1. Jean Meeus's *Astronomical Algorithms* (2nd edition) for new-moon timing
2. A dynamic **Chaitra-new-moon-based intercalation** rule for detecting leap years
3. A proleptic **Gregorian serial-day counter** for calendar arithmetic

---

## 1. Astronomical Foundation

### 1.1 New Moon Julian Day Ephemeris (JDE)

**Source:** Meeus, *Astronomical Algorithms*, Chapter 49

The function `newMoonJDE(k: Double): Double` computes the Julian Day Ephemeris (JDE) of the k-th new moon since the J2000.0 epoch reference new moon (k=0 ≈ January 6.6, 2000 UT, JDE 2451550.09766).

**Algorithm:**

```
T   = k / 1236.85                    (Julian centuries since J2000.0)

JDE = 2451550.09766
    + 29.530588861 × k               (mean synodic period)
    + 0.00015437 × T²
    - 0.000000150 × T³
    + 0.00000000073 × T⁴
```

**Perturbation Corrections (sinusoidal terms):**

Auxiliary angles (in degrees, converted to radians):
```
E  = 1.0 - 0.002516×T - 0.0000074×T²            (Earth-Sun eccentricity)
M  = 2.5534 + 29.10535670×k - 0.0000014×T² - 0.00000011×T³   (Sun's mean anomaly)
M' = 201.5643 + 385.81693528×k + 0.0107582×T² + ...           (Moon's mean anomaly)
F  = 160.7108 + 390.67050284×k - 0.0016118×T² - ...           (Moon's argument of latitude)
Ω  = 124.7746 - 1.56375588×k + 0.0020672×T² + ...             (Longitude of ascending node)
```

Correction applied to base JDE:
```
ΔT = −0.40720 sin(M')
   + 0.17241 E sin(M)
   + 0.01608 sin(2M')
   + 0.01039 sin(2F)
   + 0.00739 E sin(M'−M)
   − 0.00514 E sin(M'+M)
   + 0.00208 E² sin(2M)
   − 0.00111 sin(M'−2F)
   − 0.00057 sin(M'+2F)
   + 0.00056 E sin(2M'+M)
   − 0.00042 sin(3M')
   + 0.00042 E sin(M+2F)
   + 0.00038 E sin(M−2F)
   − 0.00024 E sin(2M'−M)
   − 0.00017 sin(Ω)
```

**Accuracy:** Sub-minute precision (± 2 minutes) for dates within a few centuries of J2000.

---

### 1.2 JDE → Gregorian Conversion (Cambodia Timezone)

**Function:** `jdeToGregorian(jde: Double): Triple<Int, Int, Int>`

Cambodia is UTC+7. The algorithm first shifts the JDE by +7/24 days, then applies the standard Julian Day Number to proleptic Gregorian calendar algorithm (Meeus, Chapter 7):

```
jdLocal = jde + 7.0 / 24.0
Z  = floor(jdLocal + 0.5)          (integer JD)

# Gregorian correction (post-Oct 15, 1582 = JD 2299161)
if Z ≥ 2299161:
    α = floor((Z − 1867216.25) / 36524.25)
    A = Z + 1 + α − floor(α/4)
else:
    A = Z

B = A + 1524
C = floor((B − 122.1) / 365.25)
D = floor(365.25 × C)
E = floor((B − D) / 30.6001)

day   = B − D − floor(30.6001 × E)
month = E − 1  (if E < 14) else E − 13
year  = C − 4716 (if month > 2) else C − 4715
```

---

## 2. Proleptic Gregorian Serial Day Counter

**Function:** `getSerialDay(year: Int, month: Int, day: Int): Int`

Converts a Gregorian date to a unique integer serial number to enable simple date arithmetic:

```
if month ≤ 2: year -= 1, month += 12

serialDay = 365×y + floor(y/4) − floor(y/100) + floor(y/400)
          + floor((153×m + 2) / 5) + day
```

The formula `(153×m + 2) / 5` computes the cumulative day offset for month `m` (counting from March = month 3). This is a standard algorithmic calendar formula (Julian Day Number variant).

**Properties:**
- Monotonically increasing — consecutive days always differ by exactly 1
- Handles leap years correctly via the `+y/4 − y/100 + y/400` correction
- The base epoch is arbitrary; only differences between serial days matter

---

## 3. Dynamic Milestone Construction (Lunisolar Intercalation)

**Function:** `buildMilestones(firstGregorianYear: Int, lastGregorianYear: Int): List<Milestone>`

This is the core intercalation algorithm that maps the solar (Gregorian) calendar to the Khmer lunisolar calendar.

### 3.1 Step 1: Enumerate New Moons

The search range for new moon index `k` is estimated using the approximate conversion:

```
kStart = round((firstGregorianYear − 2001) × 12.37) − 2
kEnd   = round((lastGregorianYear  − 1999) × 12.37) + 2
```

For each k in [kStart, kEnd], compute the Cambodia-local new moon date and collect those within the expanded year range. The list is sorted by serial day.

### 3.2 Step 2: Identify Chaitra New Moons (Khmer New Year Anchor)

The **Chaitra month** marks the beginning of the Khmer lunar year. It is defined as the lunar month whose new moon falls between **March 15** and **April 14** (inclusive):

```
isChaitra = (month == 3 AND day >= 15) OR (month == 4 AND day <= 14)
```

This window captures the traditional Khmer New Year period (around April 13–15 Gregorian).

### 3.3 Step 3: Assign Lunar Month Names and Detect Leap Years

Between consecutive Chaitra new moons:
- **12 months** → normal year (`MONTH_NAMES_NORMAL`)
- **13 months** → leap year (`MONTH_NAMES_LEAP`, with duplicated Asadha: "អាសាឍ ១" and "អាសាឍ ២")

This corresponds to the traditional **Adhikamāsa** (intercalary month) rule: when the solar year requires realignment with the lunar months, an extra Asadha is inserted.

**Leap year detection:**
```
monthsInYear = chaitraIndices[ci+1] − chaitraIndices[ci]
isLeap = (monthsInYear == 13)
```

Known leap year: 2027 CE (BE 2571 has an extra Asadha).

### 3.4 Step 4: Compute Month Lengths

Each lunar month's length in days is the serial day difference between consecutive new moons:

```
length = nextNewMoon.serialDay − thisNewMoon.serialDay
```

Typical values are 29 or 30 days (synodic month ≈ 29.53 days).

### 3.5 Step 5: Assign Buddhist Era (BE) and Zodiac

```
BE     = Chaitra_new_moon_Gregorian_year + 544
zodiac = ZODIAC_NAMES[((BE % 12) + 4 + 12) % 12]
```

The `+4` offset calibrates the 12-year zodiac cycle to the correct animal for known BE years:
- BE 2568 (2024 CE) = ឆ្នាំរោង (Dragon), index 4 → `(2568 % 12 + 4) % 12 = (0 + 4) % 12 = 4` ✓
- BE 2570 (2026 CE) = ឆ្នាំមមា (Horse), index 6 → `(2570 % 12 + 4) % 12 = (2 + 4) % 12 = 6` ✓

---

## 4. Day Lookup: `getKhmerDate(year, month, day)`

Given a Gregorian date, the algorithm:

1. Compute `sDay = getSerialDay(year, month, day)`
2. Find `ms` = last Milestone where `ms.serialDay ≤ sDay` (binary-search via `lastOrNull`)
3. Compute lunar day offset: `offset = sDay − ms.serialDay`
4. Determine lunar fortnight:
   - `isWaxing = (offset % 30) < 15`  (first half of month = waxing)
   - `displayLunarDay = if (isWaxing) (offset % 30) + 1 else (offset % 30) − 14`
   - Range: waxing 1–15, waning 1–15

5. Day of week: `dowIdx = ((sDay + 4) % 7 + 7) % 7`
   - This constant `+4` is calibrated so that `sDay` for a known Sunday maps to index 0.

---

## 5. Moon Phase Emojis

Based on `offset % 30` (position within 30-day approximation):

| offset % 30 | Emoji | Phase          |
|-------------|-------|----------------|
| 0           | 🌑    | New Moon       |
| 1–6         | 🌒    | Waxing Crescent|
| 7           | 🌓    | First Quarter  |
| 8–13        | 🌔    | Waxing Gibbous |
| 14          | 🌕    | Full Moon      |
| 15–21       | 🌖    | Waning Gibbous |
| 22          | 🌗    | Last Quarter   |
| 23–29       | 🌘    | Waning Crescent|

---

## 6. Holiday Detection

Holidays are identified by two mechanisms:

### 6.1 Fixed Gregorian Holidays

| Gregorian Condition | Holiday |
|--------------------|---------|
| month=4, day∈[14,16] | ចូលឆ្នាំថ្មីប្រពៃណីជាតិ (Khmer New Year) |
| month=11, day=9 | ទិវាបុណ្យឯករាជ្យជាតិ (Independence Day) |
| month=1, day=7 | ទិវាជ័យជម្នះ (Victory Day) |
| month=5, day=1 | ទិវាពលកម្ម (Labor Day) |
| month=6, day=18 | ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម (Queen Mother Birthday) |
| month=9, day=24 | ទិវារដ្ឋធម្មនុញ្ញ (Constitution Day) |
| month=10, day=15 | ទិវាគោរពព្រះវិញ្ញាណ (King Father Memorial) |
| month=10, day=29 | ព្រះរាជពិធីគ្រងព្រះបរមរាជ (King's Coronation) |

### 6.2 Lunar-Based Buddhist Holidays

| Condition | Holiday |
|-----------|---------|
| lunarMonth="មាឃ", waxing, day=15 | មាឃបូជា (Meak Bochea) |
| lunarMonth="ពិសាខ", waxing, day=15 | វិសាខបូជា (Visak Bochea) |
| lunarMonth="ភទ្របទ", waning, day=15 | ភ្ជុំបិណ្ឌ (Pchum Ben) |
| lunarMonth="កត្តិក", waxing, day=15 | អុំទូក (Water Festival) |

---

## 7. Auspicious Day Calculation

Auspiciousness is determined by lunar day offset position within the 30-day month:

```kotlin
isAuspicious = (offset % 30) in listOf(2, 6, 10, 11, 18, 25)
```

These correspond to specific lunar days considered traditionally lucky in the Khmer Buddhist tradition. The `auspiciousType` is derived from:

```kotlin
auspiciousType = when (displayLunarDay % 4) {
    0 -> "ពិធីមង្គលការ (Wedding)"
    1 -> "ឡើងផ្ទះថ្មី (Housewarming)"
    2 -> "បើកអាជីវកម្ម (Business)"
    3 -> "ធ្វើដំណើរ (Travel)"
}
```

---

## 8. Known Limitations and Edge Cases

| Issue | Description |
|-------|-------------|
| **Year range** | Milestones only cover 2019–2036. Dates outside this range fall back to a hardcoded default milestone (May 11, 2026). |
| **30-day approximation** | `offset % 30` simplifies the actual 29/30-day variable lunar month lengths. This means `displayLunarDay` can exceed the actual month length in some months. |
| **Khmer New Year window** | The Mar 15–Apr 14 Chaitra window is an approximation; true Khmer New Year is determined by the Hora astronomical almanac. |
| **Pchum Ben detection** | ភ្ជុំបិណ្ឌ is the 15th waning day of ភទ្របទ, but the actual Pchum Ben festival spans multiple days. Only the final day is marked. |
| **Timezone edge cases** | The UTC+7 offset is fixed; it does not account for DST (Cambodia does not observe DST, so this is correct). |
| **Zodiac formula** | The formula `((BE % 12) + 4 + 12) % 12` relies on a fixed calibration constant. This must be re-verified if the zodiac animal sequence is adjusted. |

---

## 9. Academic Cross-Reference

| Concept | Source |
|---------|--------|
| New Moon JDE formula | Jean Meeus, *Astronomical Algorithms* (2nd ed., 1998), Ch. 49, pp. 349-361 |
| JDE → Gregorian | Jean Meeus, *Astronomical Algorithms*, Ch. 7, pp. 63-64 |
| Synodic month length | 29.530588861 days (IAU standard) |
| Khmer Buddhist calendar | Lewitz, S. (1971). "Note sur la Calendrier Lunaire Cambodgien" |
| Adhikamāsa intercalation | Chatterjee, S.K. (1985). "Indian Calendric System" |

---

*Generated from analysis of `app/src/main/java/com/example/KhmerCalendarHelper.kt`*
