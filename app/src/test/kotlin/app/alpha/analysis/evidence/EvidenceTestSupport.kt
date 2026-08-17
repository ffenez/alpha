package app.alpha.analysis.evidence

/** Модель разрешения по умолчанию — та же, что у поиска пиков RC-110. */
internal val TEST_RESOLUTION = SqrtResolution()

/**
 * Наблюдённый пик с честно посчитанными неопределённостями: σ площади из
 * значимости (net/значимость), σ центроида из ширины и статистики.
 */
internal fun peakAt(
    energyKeV: Double,
    netArea: Double = 1000.0,
    significance: Double = 20.0,
    resolution: ResolutionModel = TEST_RESOLUTION,
): ObservedPeak {
    val fwhm = resolution.fwhmKeV(energyKeV)
    return ObservedPeak(
        centroidKeV = energyKeV,
        centroidUncertaintyKeV = ObservedPeak.centroidUncertaintyKeV(fwhm, netArea),
        fwhmKeV = fwhm,
        netArea = netArea,
        netAreaUncertainty = netArea / significance,
        significance = significance,
    )
}

/** Плоский континуум: [perKeV] импульсов на кэВ на всей шкале. */
internal fun flatContinuum(perKeV: Double): ContinuumModel = ContinuumModel { perKeV }

internal fun lineOf(nuclide: String, energyKeV: Double): LibraryLine =
    EvidenceLineLibrary.linesOf(nuclide).first { it.energyKeV == energyKeV }
